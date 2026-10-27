package com.example.sixsense

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt


class PantallaNotificaciones : AppCompatActivity() {

    enum class TipoEvento { APERTURA, DESARMADO, ALARMA }

    class Evento(val tipo: TipoEvento, val momento: Long)

    // ---------- Vistas ----------
    private lateinit var cardAlerta: View
    private lateinit var tvCountdown: TextView
    private lateinit var btnDesarmar: View
    private lateinit var tvEstadoHistorial: TextView
    private lateinit var cardHistorialVacio: View
    private lateinit var contenedorHistorial: LinearLayout

    private val filasHistorial = mutableListOf<View>()
    private var temporizador: CountDownTimer? = null

    private val preferencias by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_notificaciones)

        // No tiene ítem propio en la barra: ninguno queda resaltado
        NavegacionInferior.configurar(this, null)

        cardAlerta = findViewById(R.id.cardAlertaImportante)
        tvCountdown = findViewById(R.id.tvCountdown)
        btnDesarmar = findViewById(R.id.btnDesarmarAlerta)
        tvEstadoHistorial = findViewById(R.id.tvEstadoHistorial)
        cardHistorialVacio = findViewById(R.id.cardHistorialVacio)
        // Las filas del historial se agregan en el mismo contenedor que el estado vacío
        contenedorHistorial = cardHistorialVacio.parent as LinearLayout

        btnDesarmar.setOnClickListener { desarmar() }

        // Solo la primera vez: al rotar la pantalla no se debe registrar otra apertura
        if (savedInstanceState == null &&
            intent.getBooleanExtra(EXTRA_APERTURA, false)
        ) {
            intent.removeExtra(EXTRA_APERTURA)
            registrarApertura(this)
        }
    }

    override fun onResume() {
        super.onResume()
        sincronizarAlerta()
        renderHistorial()
    }

    override fun onPause() {
        temporizador?.cancel()
        temporizador = null
        super.onPause()
    }

    // =====================================================================
    // Alerta de apertura
    // =====================================================================

    /** Muestra u oculta la tarjeta según haya una alerta pendiente y reanuda la cuenta regresiva. */
    private fun sincronizarAlerta() {
        temporizador?.cancel()
        temporizador = null

        val fin = preferencias.getLong(KEY_ALERTA_FIN, 0L)
        if (fin == 0L) {
            cardAlerta.isVisible = false
            return
        }

        val restante = fin - System.currentTimeMillis()
        if (restante <= 0L) {
            // El tiempo se agotó mientras la pantalla no estaba abierta
            expirarAlerta(fin)
            return
        }

        cardAlerta.isVisible = true
        tvCountdown.text = segundosRestantes(restante)
        temporizador = object : CountDownTimer(restante, 250L) {
            override fun onTick(milisRestantes: Long) {
                tvCountdown.text = segundosRestantes(milisRestantes)
            }

            override fun onFinish() {
                expirarAlerta(fin)
            }
        }.start()
    }

    private fun segundosRestantes(milis: Long) = ((milis + 999L) / 1000L).toString()

    private fun desarmar() {
        temporizador?.cancel()
        temporizador = null
        preferencias.edit().remove(KEY_ALERTA_FIN).apply()
        agregarEvento(this, TipoEvento.DESARMADO, System.currentTimeMillis())

        cardAlerta.isVisible = false
        renderHistorial()

        Toast.makeText(this, TXT_SISTEMA_DESARMADO, Toast.LENGTH_SHORT).show()
    }

    private fun expirarAlerta(fin: Long) {
        temporizador = null
        preferencias.edit().remove(KEY_ALERTA_FIN).apply()
        agregarEvento(this, TipoEvento.ALARMA, fin)

        cardAlerta.isVisible = false
        renderHistorial()

        Toast.makeText(this, TXT_ALARMA_ACTIVADA, Toast.LENGTH_SHORT).show()
    }

    // =====================================================================
    // Historial
    // =====================================================================

    private fun renderHistorial() {
        filasHistorial.forEach { contenedorHistorial.removeView(it) }
        filasHistorial.clear()

        val eventos = leerHistorial(this)
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
            TipoEvento.APERTURA -> Triple(R.drawable.ic_warning, R.color.alarm_primary, R.color.alarm_light_bg)
            TipoEvento.DESARMADO -> Triple(R.drawable.ic_lock, R.color.green_dark, R.color.green_light_bg)
            TipoEvento.ALARMA -> Triple(R.drawable.ic_siren, R.color.alarm_primary, R.color.alarm_light_bg)
        }
        val (titulo, detalle) = when (evento.tipo) {
            TipoEvento.APERTURA -> TXT_APERTURA_TITULO to getString(R.string.alert_source)
            TipoEvento.DESARMADO -> TXT_DESARMADO_TITULO to TXT_DESARMADO_DETALLE
            TipoEvento.ALARMA -> TXT_ALARMA_TITULO to TXT_ALARMA_DETALLE
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
        columnaTexto.addView(crearTexto(titulo, 15f, R.color.green_dark, negrita = true))
        columnaTexto.addView(crearTexto(detalle, 12f, R.color.text_secondary, negrita = false))

        // Hora y día
        val columnaHora = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        columnaHora.addView(crearTexto(formatoHora(evento.momento), 12f, R.color.green_dark, negrita = true))
        columnaHora.addView(crearTexto(formatoDia(evento.momento), 11f, R.color.text_secondary, negrita = false))

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
            if (negrita) setTypeface(typeface, android.graphics.Typeface.BOLD)
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

    // =====================================================================

    companion object {
        /** Extra para abrir la pantalla registrando una apertura detectada. */
        const val EXTRA_APERTURA = "apertura_detectada"

        private const val PREFS = "notificaciones"
        private const val KEY_HISTORIAL = "historial"
        private const val KEY_ALERTA_FIN = "alerta_fin"
        private const val MAX_EVENTOS = 50

        // Valores que escribe PantallaAjustes (archivo "ajustes")
        private const val PREFS_AJUSTES = "ajustes"
        private const val KEY_AJUSTES_RETRASO = "retraso_alarma"
        private const val KEY_AJUSTES_ALERTAS = "notif_alertas"
        private const val RETRASO_POR_DEFECTO = 10


        private const val TXT_APERTURA_TITULO = "Apertura detectada"
        private const val TXT_DESARMADO_TITULO = "Sistema desarmado"
        private const val TXT_DESARMADO_DETALLE = "Desde la alerta de apertura"
        private const val TXT_ALARMA_TITULO = "Alarma activada"
        private const val TXT_ALARMA_DETALLE = "Se agotó el tiempo para desarmar"
        private const val TXT_SISTEMA_DESARMADO = "Sistema desarmado"
        private const val TXT_ALARMA_ACTIVADA = "Se activó la alarma"

        /** Intent para abrir esta pantalla y registrar una apertura (por ejemplo desde el módulo del sensor). */
        fun intentApertura(contexto: Context): Intent =
            Intent(contexto, PantallaNotificaciones::class.java)
                .putExtra(EXTRA_APERTURA, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /**
         * Registra una apertura detectada por el sensor: la anota en el historial y, si las
         * "Alertas importantes" de Ajustes están activas, inicia la cuenta regresiva con el
         * "Retraso de alarma" configurado. Se puede llamar sin que la pantalla esté abierta.
         */
        fun registrarApertura(contexto: Context) {
            val ahora = System.currentTimeMillis()
            agregarEvento(contexto, TipoEvento.APERTURA, ahora)

            val ajustes = contexto.getSharedPreferences(PREFS_AJUSTES, Context.MODE_PRIVATE)
            if (!ajustes.getBoolean(KEY_AJUSTES_ALERTAS, true)) return

            val retraso = ajustes.getInt(KEY_AJUSTES_RETRASO, RETRASO_POR_DEFECTO)
            contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_ALERTA_FIN, ahora + retraso * 1000L)
                .apply()
        }

        private fun agregarEvento(contexto: Context, tipo: TipoEvento, momento: Long) {
            val eventos = leerHistorial(contexto).toMutableList()
            eventos.add(0, Evento(tipo, momento))

            val json = JSONArray()
            eventos.take(MAX_EVENTOS).forEach {
                json.put(JSONObject().put("tipo", it.tipo.name).put("momento", it.momento))
            }
            contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HISTORIAL, json.toString())
                .apply()
        }

        private fun leerHistorial(contexto: Context): List<Evento> {
            val texto = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_HISTORIAL, null) ?: return emptyList()
            return try {
                val json = JSONArray(texto)
                (0 until json.length()).mapNotNull { indice ->
                    val objeto = json.getJSONObject(indice)
                    val tipo = TipoEvento.values().firstOrNull { it.name == objeto.optString("tipo") }
                    if (tipo == null) null else Evento(tipo, objeto.optLong("momento"))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}