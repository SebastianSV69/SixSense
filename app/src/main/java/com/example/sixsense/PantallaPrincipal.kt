package com.example.sixsense

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import java.util.Calendar
import kotlin.math.max

class PantallaPrincipal : AppCompatActivity() {

    private class Componente(
        @DrawableRes val icono: Int,
        val nombre: String,
        val rol: String,
    )

    private class FilaComponente(raiz: View) {
        val divisor: View = raiz.findViewById(R.id.divisor)
        val cajaIcono: View = raiz.findViewById(R.id.cajaIcono)
        val ivIcono: ImageView = raiz.findViewById(R.id.ivIcono)
        val tvNombre: TextView = raiz.findViewById(R.id.tvNombre)
        val tvRol: TextView = raiz.findViewById(R.id.tvRol)
        val tvLectura: TextView = raiz.findViewById(R.id.tvLectura)
        val tvEtiqueta: TextView = raiz.findViewById(R.id.tvEtiqueta)
    }

    private enum class EstadoFila { NORMAL, ALERTA, SIN_DATOS }

    private val componentes = listOf(
        Componente(R.drawable.ic_vibration, "Sensor de vibración", "Cerrojo de la puerta"),
        Componente(R.drawable.ic_magnet, "Sensor magnético", "Puerta de entrada"),
        Componente(R.drawable.ic_siren, "Alarma sonora", "Salida de alerta"),
    )
    private val filas = mutableListOf<FilaComponente>()

    private lateinit var panelEstado: View
    private lateinit var ivIconoEstado: ImageView
    private lateinit var tvTituloEstado: TextView
    private lateinit var tvDescripcionEstado: TextView
    private lateinit var btnArmar: View
    private lateinit var ivAccionArmar: ImageView
    private lateinit var tvAccionArmar: TextView
    private lateinit var alertaSistema: View
    private lateinit var tvBadge: TextView
    private lateinit var puntoEstadoHub: View
    private lateinit var tvEstadoHub: TextView
    private lateinit var tvSenal: TextView
    private lateinit var listaComponentes: LinearLayout

    private val manejador = Handler(Looper.getMainLooper())
    private val oyente = OyenteSistema { render() }

    // Refresca los "Hace X s" cada segundo mientras la pantalla está visible
    private val reloj = object : Runnable {
        override fun run() {
            render()
            manejador.postDelayed(this, 1000)
        }
    }

    private val permisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_principal)

        panelEstado = findViewById(R.id.panelEstado)
        ivIconoEstado = findViewById(R.id.ivIconoEstado)
        tvTituloEstado = findViewById(R.id.tvTituloEstado)
        tvDescripcionEstado = findViewById(R.id.tvDescripcionEstado)
        btnArmar = findViewById(R.id.btnArmar)
        ivAccionArmar = findViewById(R.id.ivAccionArmar)
        tvAccionArmar = findViewById(R.id.tvAccionArmar)
        alertaSistema = findViewById(R.id.alertaSistema)
        tvBadge = findViewById(R.id.tvBadgeDispositivos)
        puntoEstadoHub = findViewById(R.id.puntoEstadoHub)
        tvEstadoHub = findViewById(R.id.tvEstadoHub)
        tvSenal = findViewById(R.id.tvSenal)
        listaComponentes = findViewById(R.id.listaComponentes)

        ajustarBordesDelSistema()
        NavegacionInferior.configurar(this, Destino.INICIO)
        ocultarSelectorDemo()
        pedirPermisoNotificaciones()

        findViewById<TextView>(R.id.tvSaludoHora).setText(saludoSegunHora())
        findViewById<View>(R.id.btnNotificaciones).setOnClickListener {
            NavegacionInferior.abrirNotificaciones(this)
        }

        crearFilasComponentes()

        btnArmar.setOnClickListener {
            SistemaSeguridad.alternarArmado()
            SistemaSeguridad.consultarAhora()
        }

        render()
    }

    override fun onStart() {
        super.onStart()
        SistemaSeguridad.agregarOyente(oyente)
        manejador.post(reloj)
        // Si hay una alerta en curso (por ejemplo, al volver del bloqueo), va directo a Notificaciones
        if (SistemaSeguridad.enAlerta) NavegacionInferior.abrirNotificaciones(this)
    }

    override fun onStop() {
        manejador.removeCallbacks(reloj)
        SistemaSeguridad.quitarOyente(oyente)
        super.onStop()
    }

    // ---------- Configuración ----------

    /** Respeta barra de estado, notch y barra de navegación del sistema. */
    private fun ajustarBordesDelSistema() {
        val raiz = findViewById<View>(R.id.raiz)
        val barra = findViewById<View>(R.id.barraNavegacion)
        val baseInferior = barra.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            vista.updatePadding(top = b.top)
            barra.updatePadding(bottom = baseInferior + b.bottom)
            insets
        }
    }

    /** Los botones Encendido/Apagado/Sin conexión eran de prueba: ahora el estado viene de ThingSpeak. */
    private fun ocultarSelectorDemo() {
        val selector = findViewById<View>(R.id.btnEncendido).parent as View
        selector.visibility = View.GONE
        val contenedor = selector.parent as ViewGroup
        val posicion = contenedor.indexOfChild(selector)
        if (posicion > 0) contenedor.getChildAt(posicion - 1).visibility = View.GONE // divisor
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun saludoSegunHora(): Int =
        when (Calendar.getInstance()[Calendar.HOUR_OF_DAY]) {
            in 5..11 -> R.string.saludo_dias
            in 12..19 -> R.string.saludo_tardes
            else -> R.string.saludo_noches
        }

    private fun crearFilasComponentes() {
        componentes.forEachIndexed { indice, c ->
            val vista = layoutInflater.inflate(R.layout.item_componente, listaComponentes, false)
            val fila = FilaComponente(vista)
            fila.divisor.isVisible = indice > 0
            fila.ivIcono.setImageResource(c.icono)
            fila.tvNombre.text = c.nombre
            fila.tvRol.text = c.rol
            listaComponentes.addView(vista)
            filas += fila
        }
    }

    // ---------- Render ----------

    private fun render() {
        val s = SistemaSeguridad
        val armado = s.armado
        val cuenta = s.cuentaAtras
        val sonando = s.alarmaActiva
        val sinConexion = s.conexion == EstadoConexion.SIN_CONEXION
        val enAlerta = cuenta != null || sonando

        // Panel de seguridad
        panelEstado.setBackgroundResource(
            if (armado) R.drawable.bg_panel_estado_armado else R.drawable.bg_panel_estado
        )
        ivIconoEstado.setImageResource(
            when {
                enAlerta -> R.drawable.ic_alert
                armado -> R.drawable.ic_lock
                else -> R.drawable.iq_shield
            }
        )
        tvTituloEstado.setText(
            when {
                sonando -> R.string.alarma_activo
                armado -> R.string.sistema_armado
                else -> R.string.sistema_desarmado
            }
        )
        tvDescripcionEstado.text = when {
            sonando -> getString(R.string.desc_alarma_sonando)
            cuenta != null -> getString(R.string.desc_cuenta_atras, cuenta)
            sinConexion -> getString(R.string.desc_sin_conexion)
            armado -> getString(R.string.desc_armado)
            else -> getString(R.string.desc_desarmado)
        }
        // Siempre se puede desarmar; armar requiere conexión
        btnArmar.isEnabled = armado || sonando || !sinConexion
        btnArmar.setBackgroundResource(
            if (armado) R.drawable.bg_btn_desarmar else R.drawable.bg_btn_armar
        )
        ivAccionArmar.setImageResource(if (armado) R.drawable.ic_lock else R.drawable.ic_power)
        tvAccionArmar.setText(if (armado) R.string.accion_desarmar else R.string.accion_armar)

        // Alerta de desconexión
        alertaSistema.isVisible = sinConexion

        // Estado de la red
        val etiquetaEstado = getString(
            when (s.conexion) {
                EstadoConexion.CONECTADO -> R.string.estado_en_linea
                EstadoConexion.EN_ESPERA -> R.string.estado_en_espera
                EstadoConexion.SIN_CONEXION -> R.string.estado_sin_conexion
            }
        )
        tvBadge.text = etiquetaEstado
        when (s.conexion) {
            EstadoConexion.CONECTADO -> pintarBadge(R.color.verde_chip_fondo, R.color.verde_chip_texto)
            EstadoConexion.EN_ESPERA -> pintarBadge(R.color.chip_apagado_fondo, R.color.chip_apagado_texto)
            EstadoConexion.SIN_CONEXION ->
                pintarBadge(R.color.chip_desconectado_fondo, R.color.chip_desconectado_texto)
        }
        tvEstadoHub.text = getString(R.string.hub_estado_formato, etiquetaEstado)
        puntoEstadoHub.backgroundTintList = ColorStateList.valueOf(
            color(
                when (s.conexion) {
                    EstadoConexion.CONECTADO -> R.color.verde_estado
                    EstadoConexion.EN_ESPERA -> R.color.gris_estado
                    EstadoConexion.SIN_CONEXION -> R.color.rojo_estado
                }
            )
        )
        val ultima = max(s.magneticoMillis, s.vibracionMillis)
        tvSenal.text = if (ultima == 0L) "—" else s.edadCorta(ultima)

        // Sensor de vibración
        val vib = s.vibracionG
        val vibracionAlta = vib != null && vib >= Preferencias.umbralVibracionG
        pintarFila(
            filas[0],
            lectura = if (vib != null) s.formatoVibracion(vib) else "—",
            etiqueta = if (vib != null) s.haceTexto(s.vibracionMillis) else getString(R.string.sin_datos),
            estado = when {
                vib == null -> EstadoFila.SIN_DATOS
                vibracionAlta -> EstadoFila.ALERTA
                else -> EstadoFila.NORMAL
            }
        )

        // Sensor magnético
        val abierto = s.magneticoAbierto
        pintarFila(
            filas[1],
            lectura = when (abierto) {
                null -> "—"
                true -> getString(R.string.valor_abierto)
                false -> getString(R.string.valor_cerrado)
            },
            etiqueta = if (abierto != null) s.haceTexto(s.magneticoMillis) else getString(R.string.sin_datos),
            estado = when {
                abierto == null -> EstadoFila.SIN_DATOS
                abierto && armado -> EstadoFila.ALERTA
                else -> EstadoFila.NORMAL
            }
        )

        // Alarma sonora
        when {
            sonando -> pintarFila(
                filas[2], getString(R.string.alarma_sonando),
                getString(R.string.alarma_activa_corto), EstadoFila.ALERTA
            )
            cuenta != null -> pintarFila(
                filas[2], getString(R.string.alarma_cuenta_atras),
                getString(R.string.edad_segundos, cuenta), EstadoFila.ALERTA
            )
            else -> pintarFila(
                filas[2], getString(R.string.alarma_lista),
                getString(R.string.alarma_reposo), EstadoFila.NORMAL
            )
        }
    }

    private fun pintarFila(fila: FilaComponente, lectura: String, etiqueta: String, estado: EstadoFila) {
        fila.tvLectura.text = lectura
        fila.tvEtiqueta.text = etiqueta
        fila.tvLectura.setTextColor(
            color(
                when (estado) {
                    EstadoFila.NORMAL -> R.color.verde_lectura
                    EstadoFila.ALERTA -> R.color.rojo_texto
                    EstadoFila.SIN_DATOS -> R.color.gris_icono
                }
            )
        )
        fila.tvEtiqueta.setTextColor(
            color(if (estado == EstadoFila.ALERTA) R.color.rojo_secundario else R.color.texto_terciario)
        )
        fila.ivIcono.imageTintList = ColorStateList.valueOf(
            color(
                when (estado) {
                    EstadoFila.NORMAL -> R.color.verde_icono
                    EstadoFila.ALERTA -> R.color.rojo_texto
                    EstadoFila.SIN_DATOS -> R.color.gris_icono
                }
            )
        )
        fila.cajaIcono.backgroundTintList = ColorStateList.valueOf(
            color(
                when (estado) {
                    EstadoFila.NORMAL -> R.color.verde_icono_fondo
                    EstadoFila.ALERTA -> R.color.rojo_icono_fondo
                    EstadoFila.SIN_DATOS -> R.color.gris_icono_fondo
                }
            )
        )
    }

    private fun pintarBadge(@ColorRes fondo: Int, @ColorRes texto: Int) {
        tvBadge.backgroundTintList = ColorStateList.valueOf(color(fondo))
        tvBadge.setTextColor(color(texto))
    }

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(this, id)
}