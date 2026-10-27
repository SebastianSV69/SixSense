package com.example.sixsense

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class PantallaNotificaciones : AppCompatActivity() {

    // ---------- Vistas ----------
    private lateinit var cardAlerta: View
    private lateinit var tvFuenteAlerta: TextView
    private lateinit var tvTitularAlerta: TextView
    private lateinit var tvCuerpoAlerta: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var btnDesarmar: View
    private lateinit var tvEstadoHistorial: TextView
    private lateinit var cardHistorialVacio: View
    private lateinit var contenedorHistorial: LinearLayout

    private val filasHistorial = mutableListOf<View>()

    /** Para reconstruir el historial solo cuando cambia (el render corre cada segundo en la cuenta atrás). */
    private var firmaHistorial: Pair<Int, Long?>? = null

    private val oyente = OyenteSistema { render() }

    /** Durante una alerta no se puede salir de esta pantalla sin desarmar el sistema. */
    private val bloqueoAtras = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_notificaciones)

        // No tiene ítem propio en la barra: ninguno queda resaltado
        NavegacionInferior.configurar(this, null)

        cardAlerta = findViewById(R.id.cardAlertaImportante)
        tvFuenteAlerta = findViewById(R.id.tvFuenteAlerta)
        tvTitularAlerta = findViewById(R.id.tvTitularAlerta)
        tvCuerpoAlerta = findViewById(R.id.tvCuerpoAlerta)
        tvCountdown = findViewById(R.id.tvCountdown)
        btnDesarmar = findViewById(R.id.btnDesarmarAlerta)
        tvEstadoHistorial = findViewById(R.id.tvEstadoHistorial)
        cardHistorialVacio = findViewById(R.id.cardHistorialVacio)
        // Las filas del historial se agregan en el mismo contenedor que el estado vacío
        contenedorHistorial = cardHistorialVacio.parent as LinearLayout

        onBackPressedDispatcher.addCallback(this, bloqueoAtras)

        btnDesarmar.setOnClickListener {
            SistemaSeguridad.desarmar()
            Toast.makeText(this, TXT_SISTEMA_DESARMADO, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        SistemaSeguridad.agregarOyente(oyente)
        render()
    }

    override fun onStop() {
        SistemaSeguridad.quitarOyente(oyente)
        super.onStop()
    }

    // =====================================================================
    // Render
    // =====================================================================

    private fun render() {
        val s = SistemaSeguridad
        val enAlerta = s.enAlerta
        bloqueoAtras.isEnabled = enAlerta
        cardAlerta.isVisible = enAlerta
        if (enAlerta) pintarAlerta(s.cuentaAtras, s.tipoIntrusion)
        renderHistorial()
    }

    private fun pintarAlerta(cuenta: Int?, tipo: TipoIntrusion?) {
        val confirmada = tipo == TipoIntrusion.CONFIRMADA
        tvFuenteAlerta.setText(if (confirmada) R.string.alert_source_confirmada else R.string.alert_source)
        when {
            cuenta == null -> {
                // Terminó el tiempo: la alarma está sonando
                tvTitularAlerta.setText(R.string.alert_headline_alarma)
                tvCuerpoAlerta.setText(R.string.alert_body_alarma)
                tvCountdown.text = "0"
            }
            confirmada -> {
                tvTitularAlerta.setText(R.string.alert_headline_intrusion)
                tvCuerpoAlerta.setText(R.string.alert_body_intrusion)
                tvCountdown.text = cuenta.toString()
            }
            else -> {
                tvTitularAlerta.setText(R.string.alert_headline)
                tvCuerpoAlerta.setText(R.string.alert_body)
                tvCountdown.text = cuenta.toString()
            }
        }
    }

    // =====================================================================
    // Historial
    // =====================================================================

    private fun renderHistorial() {
        val eventos = HistorialEventos.lista().take(MAX_EVENTOS)
        val firma = eventos.size to eventos.firstOrNull()?.millis
        if (firma == firmaHistorial) return
        firmaHistorial = firma

        filasHistorial.forEach { contenedorHistorial.removeView(it) }
        filasHistorial.clear()

        cardHistorialVacio.isVisible = eventos.isEmpty()
        tvEstadoHistorial.text = when (eventos.size) {
            0 -> getString(R.string.history_status_empty)
            1 -> "1 evento"
            else -> "${eventos.size} eventos"
        }

        // Los más recientes primero, justo antes del estado vacío (que queda oculto)
        var posicion = contenedorHistorial.indexOfChild(cardHistorialVacio)
        eventos.forEach { evento ->
            val fila = crearFila(evento)
            contenedorHistorial.addView(fila, posicion++)
            filasHistorial += fila
        }
    }

    private fun crearFila(evento: Evento): View {
        val (icono, colorIcono, colorFondo) = when (evento.tipo) {
            TipoEvento.VIBRACION -> Triple(R.drawable.ic_vibration, R.color.alarm_primary, R.color.alarm_light_bg)
            TipoEvento.APERTURA -> Triple(R.drawable.ic_warning, R.color.alarm_primary, R.color.alarm_light_bg)
            TipoEvento.INTRUSION -> Triple(R.drawable.ic_alert, R.color.alarm_primary, R.color.alarm_light_bg)
            TipoEvento.ALARMA, TipoEvento.MANUAL ->
                Triple(R.drawable.ic_siren, R.color.alarm_primary, R.color.alarm_light_bg)
            TipoEvento.DESARME -> Triple(R.drawable.ic_lock, R.color.green_dark, R.color.green_light_bg)
        }

        val tarjeta = CardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(color(R.color.card_background))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // Icono dentro de una caja redondeada
        val cajaIcono = CardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(color(colorFondo))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        }
        val imagen = ImageView(this).apply {
            setImageResource(icono)
            imageTintList = ColorStateList.valueOf(color(colorIcono))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        cajaIcono.addView(
            imagen,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // Título y detalle
        val columnaTexto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            }
        }
        columnaTexto.addView(crearTexto(evento.titulo, 15f, R.color.green_dark, negrita = true))
        columnaTexto.addView(crearTexto(evento.detalle, 12f, R.color.text_secondary, negrita = false))

        // Hora y día
        val columnaHora = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        columnaHora.addView(crearTexto(formatoHora(evento.millis), 12f, R.color.green_dark, negrita = true))
        columnaHora.addView(crearTexto(formatoDia(evento.millis), 11f, R.color.text_secondary, negrita = false))

        fila.addView(cajaIcono)
        fila.addView(columnaTexto)
        fila.addView(columnaHora)
        tarjeta.addView(fila)
        return tarjeta
    }

    private fun crearTexto(texto: String, tamano: Float, @ColorRes colorTexto: Int, negrita: Boolean) =
        TextView(this).apply {
            text = texto
            textSize = tamano
            setTextColor(color(colorTexto))
            if (negrita) setTypeface(typeface, Typeface.BOLD)
        }

    private fun formatoHora(momento: Long): String =
        SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(momento))

    private fun formatoDia(momento: Long): String {
        val evento = Calendar.getInstance().apply { timeInMillis = momento }
        val hoy = Calendar.getInstance()
        val ayer = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            mismoDia(evento, hoy) -> "Hoy"
            mismoDia(evento, ayer) -> "Ayer"
            else -> SimpleDateFormat("dd/MM", Locale.ROOT).format(Date(momento))
        }
    }

    private fun mismoDia(a: Calendar, b: Calendar) =
        a[Calendar.YEAR] == b[Calendar.YEAR] && a[Calendar.DAY_OF_YEAR] == b[Calendar.DAY_OF_YEAR]

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).roundToInt()

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(this, id)

    private companion object {
        const val MAX_EVENTOS = 50
        const val TXT_SISTEMA_DESARMADO = "Sistema desarmado"
    }
}