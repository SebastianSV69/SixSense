package com.example.sixsense

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import java.util.Calendar
import java.util.Locale

class PantallaAjustes : AppCompatActivity() {

    private enum class Tema { CLARO, OSCURO, REACTIVO }
    private enum class Unidad { G, MS }

    // ---------- Estado ----------
    private var tema = Tema.CLARO
    private var unidad = Unidad.G
    private var progresoVibracion = 50          // 0..100  ->  0.00 g .. 1.00 g
    private var retrasoSegundos = 10
    private var alertasImportantes = true
    private var estadoConexion = true
    private var reportesDatos = false

    /** Evita que un cambio hecho desde el código se trate como un toque del usuario. */
    private var ignorarCambios = false

    // ---------- Vistas ----------
    private lateinit var tarjetasTema: Map<Tema, MaterialCardView>
    private lateinit var radiosTema: Map<Tema, RadioButton>
    private lateinit var seekSensibilidad: SeekBar
    private lateinit var tvValorVibracion: TextView
    private lateinit var tvTiempoRetraso: TextView
    private lateinit var btnUnidadG: TextView
    private lateinit var btnUnidadMS: TextView
    private lateinit var switchAlertas: SwitchCompat
    private lateinit var switchConexion: SwitchCompat
    private lateinit var switchReportes: SwitchCompat

    private val preferencias by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplica el tema guardado (si ya es el actual, no hace nada)
        aplicarTema(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_ajustes)

        NavegacionInferior.configurar(this, Destino.AJUSTES)

        tarjetasTema = mapOf(
            Tema.CLARO to findViewById(R.id.cardTemaClaro),
            Tema.OSCURO to findViewById(R.id.cardTemaOscuro),
            Tema.REACTIVO to findViewById(R.id.cardTemaReactivo)
        )
        radiosTema = mapOf(
            Tema.CLARO to findViewById(R.id.radioClaro),
            Tema.OSCURO to findViewById(R.id.radioOscuro),
            Tema.REACTIVO to findViewById(R.id.radioReactivo)
        )
        seekSensibilidad = findViewById(R.id.seekBarSensibilidad)
        tvValorVibracion = findViewById(R.id.tvValorVibracion)
        tvTiempoRetraso = findViewById(R.id.tvTiempoRetraso)
        btnUnidadG = findViewById(R.id.btnUnidadG)
        btnUnidadMS = findViewById(R.id.btnUnidadMS)
        switchAlertas = findViewById(R.id.switchAlertas)
        switchConexion = findViewById(R.id.switchConexion)
        switchReportes = findViewById(R.id.switchReportes)

        cargarPreferencias()
        configurarApariencia()
        configurarUmbrales()
        configurarUnidades()
        configurarNotificaciones()
    }

    // =====================================================================
    // Preferencias
    // =====================================================================

    private fun cargarPreferencias() {
        tema = Tema.values().firstOrNull { it.name == preferencias.getString(KEY_TEMA, null) }
            ?: Tema.CLARO
        unidad = Unidad.values().firstOrNull { it.name == preferencias.getString(KEY_UNIDAD, null) }
            ?: Unidad.G
        progresoVibracion = preferencias.getInt(KEY_VIBRACION, 50).coerceIn(1, 100)
        retrasoSegundos = preferencias.getInt(KEY_RETRASO, 10)
        alertasImportantes = preferencias.getBoolean(KEY_ALERTAS, true)
        estadoConexion = preferencias.getBoolean(KEY_CONEXION, true)
        reportesDatos = preferencias.getBoolean(KEY_REPORTES, false)
    }

    private fun guardarPreferencias() {
        preferencias.edit()
            .putString(KEY_TEMA, tema.name)
            .putString(KEY_UNIDAD, unidad.name)
            .putInt(KEY_VIBRACION, progresoVibracion)
            .putInt(KEY_RETRASO, retrasoSegundos)
            .putBoolean(KEY_ALERTAS, alertasImportantes)
            .putBoolean(KEY_CONEXION, estadoConexion)
            .putBoolean(KEY_REPORTES, reportesDatos)
            .apply()
    }

    // =====================================================================
    // 1. Apariencia
    // =====================================================================

    private fun configurarApariencia() {
        renderTema()
        tarjetasTema.forEach { (opcion, tarjeta) ->
            tarjeta.setOnClickListener {
                if (opcion == tema) return@setOnClickListener
                tema = opcion
                guardarPreferencias()
                renderTema()
                // Cambiar el modo nocturno recrea la pantalla; el estado se restaura desde las preferencias
                aplicarTema(this)
            }
        }
    }

    /** Selección única: la tarjeta elegida se resalta y su radio queda marcado. */
    private fun renderTema() {
        tarjetasTema.forEach { (opcion, tarjeta) ->
            val seleccionada = opcion == tema
            radiosTema.getValue(opcion).isChecked = seleccionada
            tarjeta.setCardBackgroundColor(
                color(if (seleccionada) R.color.green_light_bg else R.color.card_background)
            )
            tarjeta.strokeColor = color(if (seleccionada) R.color.green_dark else R.color.green_light_bg)
        }
    }

    // =====================================================================
    // 2. Umbrales de alerta
    // =====================================================================

    private fun configurarUmbrales() {
        seekSensibilidad.progress = progresoVibracion
        renderVibracion()
        renderRetraso()

        seekSensibilidad.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(barra: SeekBar, progreso: Int, delUsuario: Boolean) {
                // Un umbral de 0 haría saltar la alerta con cualquier movimiento: mínimo 0.01 g
                val valor = progreso.coerceAtLeast(1)
                if (valor != progreso) barra.progress = valor
                progresoVibracion = valor
                renderVibracion()
            }

            override fun onStartTrackingTouch(barra: SeekBar) {}

            override fun onStopTrackingTouch(barra: SeekBar) {
                guardarPreferencias()
            }
        })

        findViewById<MaterialCardView>(R.id.cardSelectorTiempo).setOnClickListener {
            elegirRetraso()
        }
    }

    private fun elegirRetraso() {
        val etiquetas = OPCIONES_RETRASO.map { textoSegundos(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.retraso_title)
            .setSingleChoiceItems(etiquetas, OPCIONES_RETRASO.indexOf(retrasoSegundos)) { dialogo, posicion ->
                retrasoSegundos = OPCIONES_RETRASO[posicion]
                guardarPreferencias()
                renderRetraso()
                dialogo.dismiss()
            }
            .setNegativeButton(R.string.accion_cancelar, null)
            .show()
    }

    private fun renderVibracion() {
        val gravedades = progresoVibracion / 100.0
        tvValorVibracion.text = when (unidad) {
            Unidad.G ->
                String.format(Locale.ROOT, "%.2f %s", gravedades, getString(R.string.unidad_g))
            Unidad.MS ->
                String.format(Locale.ROOT, "%.2f %s", gravedades * GRAVEDAD, getString(R.string.unidad_ms))
        }
    }

    private fun renderRetraso() {
        tvTiempoRetraso.text = textoSegundos(retrasoSegundos)
    }

    private fun textoSegundos(segundos: Int) = "$segundos $TXT_SEGUNDOS"

    // =====================================================================
    // 3. Unidades
    // =====================================================================

    private fun configurarUnidades() {
        renderUnidades()
        btnUnidadG.setOnClickListener { cambiarUnidad(Unidad.G) }
        btnUnidadMS.setOnClickListener { cambiarUnidad(Unidad.MS) }
    }

    private fun cambiarUnidad(nueva: Unidad) {
        if (nueva == unidad) return
        unidad = nueva
        guardarPreferencias()
        renderUnidades()
        renderVibracion()
    }

    private fun renderUnidades() {
        marcarSelector(btnUnidadG, unidad == Unidad.G)
        marcarSelector(btnUnidadMS, unidad == Unidad.MS)
    }

    private fun marcarSelector(boton: TextView, seleccionado: Boolean) {
        boton.isSelected = seleccionado
        if (seleccionado) {
            boton.setBackgroundResource(R.drawable.bg_tab_selected)
            boton.setTextColor(color(R.color.green_dark))
            boton.setTypeface(null, Typeface.BOLD)
        } else {
            boton.background = null
            boton.setTextColor(color(R.color.text_secondary))
            boton.setTypeface(null, Typeface.NORMAL)
        }
    }

    // =====================================================================
    // 4. Notificaciones
    // =====================================================================

    private fun configurarNotificaciones() {
        // Primero se pinta el estado guardado y después se enganchan los listeners,
        // para que pintar no dispare un guardado.
        ignorarCambios = true
        switchAlertas.isChecked = alertasImportantes
        switchConexion.isChecked = estadoConexion
        switchReportes.isChecked = reportesDatos
        ignorarCambios = false

        switchAlertas.setOnCheckedChangeListener { _, activo ->
            if (ignorarCambios) return@setOnCheckedChangeListener
            if (activo) {
                alertasImportantes = true
                guardarPreferencias()
            } else {
                confirmarApagarAlertas()
            }
        }
        switchConexion.setOnCheckedChangeListener { _, activo ->
            if (ignorarCambios) return@setOnCheckedChangeListener
            estadoConexion = activo
            guardarPreferencias()
        }
        switchReportes.setOnCheckedChangeListener { _, activo ->
            if (ignorarCambios) return@setOnCheckedChangeListener
            reportesDatos = activo
            guardarPreferencias()
        }
    }

    /** Apagar las alertas importantes deja al sistema sin avisos de apertura: se pide confirmación. */
    private fun confirmarApagarAlertas() {
        AlertDialog.Builder(this)
            .setTitle(R.string.alertas_title)
            .setMessage(TXT_CONFIRMAR_APAGAR_ALERTAS)
            .setNegativeButton(R.string.accion_cancelar) { _, _ -> restaurarAlertas() }
            .setPositiveButton(TXT_DESACTIVAR) { _, _ ->
                alertasImportantes = false
                guardarPreferencias()
            }
            .setOnCancelListener { restaurarAlertas() }
            .show()
    }

    private fun restaurarAlertas() {
        ignorarCambios = true
        switchAlertas.isChecked = true
        ignorarCambios = false
    }

    // =====================================================================

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(this, id)

    companion object {
        private const val PREFS = "ajustes"
        private const val KEY_TEMA = "tema"
        private const val KEY_UNIDAD = "unidad"
        private const val KEY_VIBRACION = "vibracion_minima"
        private const val KEY_RETRASO = "retraso_alarma"
        private const val KEY_ALERTAS = "notif_alertas"
        private const val KEY_CONEXION = "notif_conexion"
        private const val KEY_REPORTES = "notif_reportes"

        private const val GRAVEDAD = 9.80665                       // 1 g en m/s²
        private val OPCIONES_RETRASO = intArrayOf(5, 10, 15, 20, 30, 45, 60)

        private const val TXT_SEGUNDOS = "segundos"
        private const val TXT_DESACTIVAR = "Desactivar"
        private const val TXT_CONFIRMAR_APAGAR_ALERTAS =
            "Si desactivas las alertas importantes no recibirás avisos de aperturas ni de la activación de la alarma."

        /**
         * Aplica el tema guardado (Claro / Oscuro / Reactivo) a toda la app.
         * Llámalo desde otra pantalla (por ejemplo PantallaBloqueo.onCreate) si quieres que el tema
         * se aplique desde el arranque y no solo al entrar a Ajustes.
         */
        fun aplicarTema(contexto: Context) {
            val guardado = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TEMA, null)
            val tema = Tema.values().firstOrNull { it.name == guardado } ?: Tema.CLARO

            val oscuro = when (tema) {
                Tema.CLARO -> false
                Tema.OSCURO -> true
                Tema.REACTIVO -> {
                    // "Según tu hora local": oscuro de 19:00 a 05:59
                    val hora = Calendar.getInstance()[Calendar.HOUR_OF_DAY]
                    hora >= 19 || hora < 6
                }
            }
            AppCompatDelegate.setDefaultNightMode(
                if (oscuro) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }
}