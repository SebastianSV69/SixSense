package com.example.sixsense

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import java.util.Calendar

class PantallaPrincipal : AppCompatActivity() {

    private enum class EstadoHub { ENCENDIDO, APAGADO, DESCONECTADO }

    private class Componente(
        @param:DrawableRes val icono: Int,
        val nombre: String,
        val rol: String,
        val lectura: String,
        val etiquetaLectura: String,
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

    private var armado = false
    private var estadoHub = EstadoHub.ENCENDIDO

    private val componentes = listOf(
        Componente(R.drawable.ic_vibration, "Sensor de vibración", "Zona principal", "0.02 g", "Sin vibraciones"),
        Componente(R.drawable.ic_magnet, "Sensor magnético", "Puerta de entrada", "Cerrado", "Hace 2 s"),
        Componente(R.drawable.ic_siren, "Alarma sonora", "Salida de alerta", "Lista", "0 dB")
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
    private lateinit var btnEncendido: TextView
    private lateinit var btnApagado: TextView
    private lateinit var btnSinConexion: TextView
    private lateinit var listaComponentes: LinearLayout

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
        btnEncendido = findViewById(R.id.btnEncendido)
        btnApagado = findViewById(R.id.btnApagado)
        btnSinConexion = findViewById(R.id.btnSinConexion)
        listaComponentes = findViewById(R.id.listaComponentes)

        ajustarBordesDelSistema()
        configurarBarraNavegacion()

        findViewById<TextView>(R.id.tvSaludoHora).setText(saludoSegunHora())
        findViewById<View>(R.id.btnNotificaciones).setOnClickListener {
            NavegacionInferior.abrirNotificaciones(this)
        }

        crearFilasComponentes()

        btnArmar.setOnClickListener {
            armado = !armado
            render()
        }
        btnEncendido.setOnClickListener { cambiarEstadoHub(EstadoHub.ENCENDIDO) }
        btnApagado.setOnClickListener { cambiarEstadoHub(EstadoHub.APAGADO) }
        btnSinConexion.setOnClickListener { cambiarEstadoHub(EstadoHub.DESCONECTADO) }

        render()
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

    private fun configurarBarraNavegacion() {
        NavegacionInferior.configurar(this, Destino.INICIO)
    }

    private fun avisoProximamente() {
        Toast.makeText(this, R.string.proximamente, Toast.LENGTH_SHORT).show()
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

    private fun cambiarEstadoHub(nuevo: EstadoHub) {
        estadoHub = nuevo
        if (nuevo != EstadoHub.ENCENDIDO) armado = false
        render()
    }

    // ---------- Render ----------

    private fun render() {
        val encendido = estadoHub == EstadoHub.ENCENDIDO

        // Panel de seguridad
        panelEstado.setBackgroundResource(
            if (armado) R.drawable.bg_panel_estado_armado else R.drawable.bg_panel_estado
        )
        ivIconoEstado.setImageResource(if (armado) R.drawable.ic_lock else R.drawable.iq_shield)
        tvTituloEstado.setText(if (armado) R.string.sistema_armado else R.string.sistema_desarmado)
        tvDescripcionEstado.setText(
            when {
                !encendido -> R.string.desc_apagado
                armado -> R.string.desc_armado
                else -> R.string.desc_desarmado
            }
        )
        btnArmar.isEnabled = encendido
        btnArmar.setBackgroundResource(
            if (armado) R.drawable.bg_btn_desarmar else R.drawable.bg_btn_armar
        )
        ivAccionArmar.setImageResource(if (armado) R.drawable.ic_lock else R.drawable.ic_power)
        tvAccionArmar.setText(if (armado) R.string.accion_desarmar else R.string.accion_armar)

        // Alerta de desconexión
        alertaSistema.isVisible = estadoHub == EstadoHub.DESCONECTADO

        // Badge de la sección
        when (estadoHub) {
            EstadoHub.ENCENDIDO -> {
                tvBadge.setText(R.string.badge_activos)
                pintarBadge(R.color.verde_chip_fondo, R.color.verde_chip_texto)
            }
            EstadoHub.APAGADO -> {
                tvBadge.setText(R.string.hub_apagado)
                pintarBadge(R.color.chip_apagado_fondo, R.color.chip_apagado_texto)
            }
            EstadoHub.DESCONECTADO -> {
                tvBadge.setText(R.string.hub_desconectado)
                pintarBadge(R.color.chip_desconectado_fondo, R.color.chip_desconectado_texto)
            }
        }

        // Tarjeta del Arduino
        val etiquetaEstado = getString(
            when (estadoHub) {
                EstadoHub.ENCENDIDO -> R.string.hub_encendido
                EstadoHub.APAGADO -> R.string.hub_apagado
                EstadoHub.DESCONECTADO -> R.string.hub_desconectado
            }
        )
        tvEstadoHub.text = getString(R.string.hub_estado_formato, etiquetaEstado)
        puntoEstadoHub.backgroundTintList = ColorStateList.valueOf(
            color(
                when (estadoHub) {
                    EstadoHub.ENCENDIDO -> R.color.verde_estado
                    EstadoHub.APAGADO -> R.color.gris_estado
                    EstadoHub.DESCONECTADO -> R.color.rojo_estado
                }
            )
        )
        tvSenal.text = if (encendido) "98%" else "—"

        // Selector Encendido / Apagado / Sin conexión
        marcarSelector(btnEncendido, estadoHub == EstadoHub.ENCENDIDO)
        marcarSelector(btnApagado, estadoHub == EstadoHub.APAGADO)
        marcarSelector(btnSinConexion, estadoHub == EstadoHub.DESCONECTADO)

        // Filas de componentes
        val estadoComponente = getString(
            when (estadoHub) {
                EstadoHub.ENCENDIDO -> R.string.comp_conectado
                EstadoHub.APAGADO -> R.string.comp_en_espera
                EstadoHub.DESCONECTADO -> R.string.comp_sin_senal
            }
        )
        filas.forEachIndexed { indice, fila ->
            val c = componentes[indice]
            fila.tvLectura.text = if (encendido) c.lectura else "—"
            fila.tvEtiqueta.text = if (encendido) c.etiquetaLectura else estadoComponente
            fila.tvLectura.setTextColor(color(if (encendido) R.color.verde_lectura else R.color.gris_icono))
            fila.tvEtiqueta.setTextColor(
                color(if (encendido) R.color.texto_terciario else R.color.verde_etiqueta_estado)
            )
            fila.ivIcono.imageTintList =
                ColorStateList.valueOf(color(if (encendido) R.color.verde_icono else R.color.gris_icono))
            fila.cajaIcono.backgroundTintList = ColorStateList.valueOf(
                color(if (encendido) R.color.verde_icono_fondo else R.color.gris_icono_fondo)
            )
        }
    }

    private fun pintarBadge(@ColorRes fondo: Int, @ColorRes texto: Int) {
        tvBadge.backgroundTintList = ColorStateList.valueOf(color(fondo))
        tvBadge.setTextColor(color(texto))
    }

    private fun marcarSelector(boton: TextView, activo: Boolean) {
        if (activo) {
            boton.setBackgroundResource(R.drawable.bg_selector_activo)
            boton.setTextColor(
                color(if (estadoHub == EstadoHub.DESCONECTADO) R.color.rojo_seleccion else R.color.verde_seleccion)
            )
        } else {
            boton.background = null
            boton.setTextColor(color(R.color.texto_selector_inactivo))
        }
    }

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(this, id)
}