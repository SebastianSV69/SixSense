package com.example.sixsense

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pantalla de funciones:
 *  1. Programación de horarios (armado / desarmado automático): interruptor, horas y días.
 *  2. Análisis del sistema: gráfica de barras de las últimas 24 h (datos de ThingSpeak).
 *  3. Alarma manual: confirmación y botón para detenerla. La sirena la gestiona SistemaSeguridad.
 */
class PantallaFunciones : AppCompatActivity() {

    private enum class Pestana { VIBRACION, EVENTOS, SENAL }

    /** Datos de una pestaña: altura de cada barra (en dp, máx. 100) y el valor que se muestra arriba. */
    private class Serie(val barras: IntArray, val resumen: String)

    /** Hasta que lleguen los datos de ThingSpeak, se muestra una línea base sin valores. */
    private var series: Map<Pestana, Serie> = Pestana.values().associateWith {
        Serie(IntArray(NUM_BARRAS) { ALTURA_MINIMA }, TXT_SIN_DATOS)
    }

    // Orden Lunes..Domingo (mismo orden que las vistas de los días)
    private val idsDias = intArrayOf(
        R.id.dayL, R.id.dayM, R.id.dayX, R.id.dayJ, R.id.dayV, R.id.dayS, R.id.dayD
    )
    private val nombresDias = intArrayOf(
        R.string.cd_day_l, R.string.cd_day_m, R.string.cd_day_x, R.string.cd_day_j,
        R.string.cd_day_v, R.string.cd_day_s, R.string.cd_day_d
    )

    // ---------- Estado ----------
    private var horariosActivos = true
    private var minutosArmar = 22 * 60
    private var minutosDesarmar = 7 * 60
    private var diasMascara = 0b0011111          // bit 0 = lunes ... bit 6 = domingo (por defecto L-V)
    private var pestanaActual = Pestana.VIBRACION

    private var animadorGrafica: ValueAnimator? = null
    private val oyente = OyenteSistema { renderAlarma() }

    // ---------- Vistas ----------
    private lateinit var scroll: ScrollView
    private lateinit var switchHorarios: SwitchCompat
    private lateinit var btnArmar: View
    private lateinit var btnDesarmar: View
    private lateinit var tvHoraArmar: TextView
    private lateinit var tvHoraDesarmar: TextView
    private lateinit var layoutDias: View
    private lateinit var dias: List<TextView>
    private lateinit var tvEstadoHorarios: TextView
    private lateinit var tvPromedio: TextView
    private lateinit var pestanas: Map<Pestana, TextView>
    private lateinit var layoutChart: ViewGroup
    private lateinit var btnAlarma: MaterialButton

    private val preferencias by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_funciones)

        scroll = findViewById(R.id.scrollFunciones)
        switchHorarios = findViewById(R.id.switchHorarios)
        btnArmar = findViewById(R.id.btnConfigurarArmar)
        btnDesarmar = findViewById(R.id.btnConfigurarDesarmar)
        tvHoraArmar = findViewById(R.id.tvHoraArmar)
        tvHoraDesarmar = findViewById(R.id.tvHoraDesarmar)
        layoutDias = findViewById(R.id.layoutDias)
        dias = idsDias.map { findViewById<TextView>(it) }
        tvEstadoHorarios = findViewById(R.id.tvEstadoHorarios)
        tvPromedio = findViewById(R.id.tvPromedio)
        pestanas = mapOf(
            Pestana.VIBRACION to findViewById(R.id.tabVibracion),
            Pestana.EVENTOS to findViewById(R.id.tabEventos),
            Pestana.SENAL to findViewById(R.id.tabSenal)
        )
        layoutChart = findViewById(R.id.layoutChart)
        btnAlarma = findViewById(R.id.btnActivarAlarma)

        cargarPreferencias()
        NavegacionInferior.configurar(this, Destino.FUNCIONES)
        configurarHorarios()
        crearBarras()
        configurarAnalisis()
        configurarAlarma()

        findViewById<View>(R.id.btnAnaliticas).setOnClickListener { irAlAnalisis() }
    }

    override fun onStart() {
        super.onStart()
        SistemaSeguridad.agregarOyente(oyente)
        renderAlarma()
    }

    override fun onResume() {
        super.onResume()
        // "hoy / mañana" cambia con el tiempo
        renderEstadoHorarios()
        cargarAnalisis()
    }

    override fun onStop() {
        SistemaSeguridad.quitarOyente(oyente)
        super.onStop()
    }

    override fun onDestroy() {
        animadorGrafica?.cancel()
        super.onDestroy()
    }

    // =====================================================================
    // Botón de analíticas
    // =====================================================================

    /** Botón superior derecho: baja hasta la tarjeta "Análisis del sistema". */
    private fun irAlAnalisis() {
        var vista: View = layoutChart
        while (vista !is CardView) {
            vista = vista.parent as? View ?: return
        }
        var arriba = vista.top
        var padre = vista.parent
        while (padre is View && padre !== scroll) {
            arriba += padre.top
            padre = padre.parent
        }
        scroll.smoothScrollTo(0, arriba)
    }

    // =====================================================================
    // 1. Programación de horarios
    // =====================================================================

    private fun cargarPreferencias() {
        horariosActivos = preferencias.getBoolean(KEY_ACTIVOS, true)
        minutosArmar = preferencias.getInt(KEY_HORA_ARMAR, 22 * 60)
        minutosDesarmar = preferencias.getInt(KEY_HORA_DESARMAR, 7 * 60)
        diasMascara = preferencias.getInt(KEY_DIAS, 0b0011111)
    }

    private fun guardarPreferencias() {
        preferencias.edit()
            .putBoolean(KEY_ACTIVOS, horariosActivos)
            .putInt(KEY_HORA_ARMAR, minutosArmar)
            .putInt(KEY_HORA_DESARMAR, minutosDesarmar)
            .putInt(KEY_DIAS, diasMascara)
            .apply()
    }

    private fun configurarHorarios() {
        // Primero se pinta el estado guardado y después se engancha el listener,
        // para que pintar no dispare un guardado.
        switchHorarios.isChecked = horariosActivos
        renderHorarios()

        switchHorarios.setOnCheckedChangeListener { _, activo ->
            horariosActivos = activo
            guardarPreferencias()
            renderHorarios()
        }

        btnArmar.setOnClickListener {
            elegirHora(R.string.horarios_armar_label, minutosArmar) { nuevo ->
                if (nuevo == minutosDesarmar) {
                    avisoHorasIguales()
                } else {
                    minutosArmar = nuevo
                    guardarPreferencias()
                    renderHorarios()
                }
            }
        }
        btnDesarmar.setOnClickListener {
            elegirHora(R.string.horarios_desarmar_label, minutosDesarmar) { nuevo ->
                if (nuevo == minutosArmar) {
                    avisoHorasIguales()
                } else {
                    minutosDesarmar = nuevo
                    guardarPreferencias()
                    renderHorarios()
                }
            }
        }

        dias.forEachIndexed { indice, vista ->
            vista.setOnClickListener {
                if (!horariosActivos) return@setOnClickListener
                diasMascara = diasMascara xor (1 shl indice)
                guardarPreferencias()
                renderHorarios()
            }
        }
    }

    private fun elegirHora(titulo: Int, minutosActuales: Int, alAceptar: (Int) -> Unit) {
        if (!horariosActivos) return
        val selector = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(minutosActuales / 60)
            .setMinute(minutosActuales % 60)
            .setTitleText(getString(titulo))
            .build()
        selector.addOnPositiveButtonClickListener {
            alAceptar(selector.hour * 60 + selector.minute)
        }
        selector.show(supportFragmentManager, TAG_SELECTOR_HORA)
    }

    private fun avisoHorasIguales() {
        Toast.makeText(this, TXT_HORAS_IGUALES, Toast.LENGTH_SHORT).show()
    }

    private fun renderHorarios() {
        tvHoraArmar.text = formatoHora(minutosArmar)
        tvHoraDesarmar.text = formatoHora(minutosDesarmar)

        // Con la programación apagada, la sección se ve atenuada y no responde
        val alfa = if (horariosActivos) 1f else 0.4f
        btnArmar.isEnabled = horariosActivos
        btnDesarmar.isEnabled = horariosActivos
        btnArmar.alpha = alfa
        btnDesarmar.alpha = alfa
        layoutDias.alpha = alfa

        dias.forEachIndexed { indice, vista ->
            val activo = diaActivo(indice)
            vista.isEnabled = horariosActivos
            vista.setBackgroundResource(
                if (activo) R.drawable.bg_day_active else R.drawable.bg_day_inactive
            )
            vista.setTextColor(color(if (activo) R.color.card_background else R.color.text_secondary))
            ViewCompat.setStateDescription(vista, if (activo) TXT_DIA_ACTIVO else TXT_DIA_INACTIVO)
        }

        renderEstadoHorarios()
    }

    private fun renderEstadoHorarios() {
        val cantidad = Integer.bitCount(diasMascara)
        tvEstadoHorarios.text = when {
            !horariosActivos -> "●  $TXT_PROGRAMACION_APAGADA"
            cantidad == 0 -> "●  $TXT_SIN_DIAS"
            else -> {
                val resumen = if (cantidad == 1) "1 día activo" else "$cantidad días activos"
                "●  $resumen. Próximo armado ${textoProximoArmado()}"
            }
        }
    }

    /** Devuelve algo como "hoy a las 22:00", "mañana a las 22:00" o "el lunes a las 22:00". */
    private fun textoProximoArmado(): String {
        val ahora = Calendar.getInstance()
        val minutosAhora = ahora[Calendar.HOUR_OF_DAY] * 60 + ahora[Calendar.MINUTE]
        val hoy = indiceDia(ahora[Calendar.DAY_OF_WEEK])

        for (desplazamiento in 0..7) {
            val indice = (hoy + desplazamiento) % 7
            if (!diaActivo(indice)) continue
            if (desplazamiento == 0 && minutosArmar <= minutosAhora) continue

            val cuando = when (desplazamiento) {
                0 -> "hoy"
                1 -> "mañana"
                else -> "el " + getString(nombresDias[indice]).lowercase(Locale("es"))
            }
            return "$cuando a las ${formatoHora(minutosArmar)}"
        }
        return "a las ${formatoHora(minutosArmar)}"
    }

    /** Calendar.DAY_OF_WEEK (domingo = 1) -> índice propio (lunes = 0 ... domingo = 6). */
    private fun indiceDia(diaCalendar: Int): Int = (diaCalendar + 5) % 7

    private fun diaActivo(indice: Int) = diasMascara and (1 shl indice) != 0

    private fun formatoHora(minutos: Int) =
        String.format(Locale.ROOT, "%02d:%02d", minutos / 60, minutos % 60)

    // =====================================================================
    // 2. Análisis del sistema (últimas 24 h, en 10 tramos de 2,4 h)
    // =====================================================================

    /** El XML deja la gráfica vacía: las barras se crean aquí. */
    private fun crearBarras() {
        if (layoutChart.childCount > 0) return
        val margen = (3 * resources.displayMetrics.density).roundToInt()
        repeat(NUM_BARRAS) {
            layoutChart.addView(
                View(this),
                LinearLayout.LayoutParams(0, 0, 1f).apply {
                    marginStart = margen
                    marginEnd = margen
                }
            )
        }
    }

    private fun configurarAnalisis() {
        pestanas.forEach { (pestana, vista) ->
            vista.setOnClickListener { seleccionarPestana(pestana, animar = true) }
        }
        seleccionarPestana(pestanaActual, animar = false)
    }

    /** Pide a ThingSpeak las lecturas de las últimas 24 h y redibuja la gráfica. */
    private fun cargarAnalisis() {
        SistemaSeguridad.consultarUltimas24h { lecturas ->
            if (isFinishing || isDestroyed || lecturas == null) return@consultarUltimas24h
            series = construirSeries(lecturas)
            seleccionarPestana(pestanaActual, animar = true)
        }
    }

    private fun construirSeries(lecturas: List<ThingSpeakApi.Lectura>): Map<Pestana, Serie> {
        val tramo = VENTANA_MS / NUM_BARRAS

        // Lecturas: reloj de ThingSpeak
        val inicioServidor = SistemaSeguridad.ahoraServidor() - VENTANA_MS
        val maxVibracion = DoubleArray(NUM_BARRAS)
        val lecturasPorTramo = DoubleArray(NUM_BARRAS)
        var sumaVibracion = 0.0
        var cantidadVibracion = 0
        var totalLecturas = 0
        lecturas.forEach { l ->
            if (l.creadoMillis < inicioServidor) return@forEach
            val i = ((l.creadoMillis - inicioServidor) / tramo).toInt().coerceIn(0, NUM_BARRAS - 1)
            lecturasPorTramo[i] += 1.0
            totalLecturas++
            l.vibracionG?.let {
                maxVibracion[i] = max(maxVibracion[i], it)
                sumaVibracion += it
                cantidadVibracion++
            }
        }

        // Eventos: reloj del teléfono (así se guardan en el historial)
        val inicioTelefono = System.currentTimeMillis() - VENTANA_MS
        val eventosPorTramo = DoubleArray(NUM_BARRAS)
        var totalEventos = 0
        HistorialEventos.lista().forEach { e ->
            if (e.millis < inicioTelefono) return@forEach
            val i = ((e.millis - inicioTelefono) / tramo).toInt().coerceIn(0, NUM_BARRAS - 1)
            eventosPorTramo[i] += 1.0
            totalEventos++
        }

        val resumenVibracion = if (cantidadVibracion > 0) {
            SistemaSeguridad.formatoVibracion(sumaVibracion / cantidadVibracion)
        } else {
            TXT_SIN_DATOS
        }
        return mapOf(
            Pestana.VIBRACION to Serie(alturas(maxVibracion), resumenVibracion),
            Pestana.EVENTOS to Serie(
                alturas(eventosPorTramo),
                if (totalEventos == 1) "1 evento" else "$totalEventos eventos"
            ),
            Pestana.SENAL to Serie(
                alturas(lecturasPorTramo),
                if (totalLecturas == 1) "1 lectura" else "$totalLecturas lecturas"
            )
        )
    }

    /** Escala los valores a barras de hasta 100 dp; un tramo sin datos queda como línea base. */
    private fun alturas(valores: DoubleArray): IntArray {
        val maximo = valores.maxOrNull() ?: 0.0
        return IntArray(valores.size) { i ->
            if (maximo <= 0.0 || valores[i] <= 0.0) {
                ALTURA_MINIMA
            } else {
                max(ALTURA_MINIMA * 2, (valores[i] / maximo * 100).roundToInt())
            }
        }
    }

    private fun seleccionarPestana(pestana: Pestana, animar: Boolean) {
        pestanaActual = pestana

        pestanas.forEach { (p, vista) ->
            val seleccionada = p == pestana
            vista.isSelected = seleccionada
            if (seleccionada) {
                vista.setBackgroundResource(R.drawable.bg_tab_selected)
                vista.setTextColor(color(R.color.green_dark))
                vista.setTypeface(null, Typeface.BOLD)
            } else {
                vista.background = null
                vista.setTextColor(color(R.color.text_secondary))
                vista.setTypeface(null, Typeface.NORMAL)
            }
        }

        val serie = series.getValue(pestana)
        tvPromedio.text = serie.resumen
        dibujarGrafica(serie, animar)
    }

    private fun dibujarGrafica(serie: Serie, animar: Boolean) {
        animadorGrafica?.cancel()

        val barras = (0 until layoutChart.childCount).map { layoutChart.getChildAt(it) }
        val densidad = resources.displayMetrics.density
        val maximo = serie.barras.maxOrNull() ?: 0

        // La barra más alta se resalta en verde oscuro, el resto en verde apagado
        barras.forEachIndexed { indice, barra ->
            val valor = serie.barras.getOrElse(indice) { 0 }
            barra.setBackgroundColor(
                color(if (valor == maximo) R.color.green_dark else R.color.green_muted)
            )
        }

        val desde = barras.map { it.layoutParams.height }
        val hasta = barras.indices.map { indice ->
            (serie.barras.getOrElse(indice) { 0 } * densidad).roundToInt()
        }

        if (!animar) {
            barras.forEachIndexed { i, barra -> barra.updateLayoutParams { height = hasta[i] } }
            return
        }

        animadorGrafica = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener { animador ->
                val avance = animador.animatedValue as Float
                barras.forEachIndexed { i, barra ->
                    barra.updateLayoutParams {
                        height = (desde[i] + (hasta[i] - desde[i]) * avance).roundToInt()
                    }
                }
            }
            start()
        }
    }

    // =====================================================================
    // 3. Alarma manual
    // =====================================================================

    private fun configurarAlarma() {
        renderAlarma()
        btnAlarma.setOnClickListener {
            if (SistemaSeguridad.alarmaActiva) {
                // La manual se detiene; una alarma automática (intrusión) exige desarmar el sistema
                val eraManual = SistemaSeguridad.alarmaManual
                SistemaSeguridad.detenerAlarmaActual()
                Toast.makeText(
                    this,
                    if (eraManual) TXT_ALARMA_DETENIDA else TXT_SISTEMA_DESARMADO,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                confirmarAlarma()
            }
        }
    }

    private fun confirmarAlarma() {
        AlertDialog.Builder(this)
            .setTitle(R.string.alarma_title)
            .setMessage(R.string.alarma_warning)
            .setNegativeButton(R.string.accion_cancelar, null)
            .setPositiveButton(R.string.alarma_button) { _, _ ->
                SistemaSeguridad.activarAlarmaManual()
                Toast.makeText(this, TXT_ALARMA_ACTIVADA, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun renderAlarma() {
        if (SistemaSeguridad.alarmaActiva) {
            btnAlarma.text = if (SistemaSeguridad.alarmaManual) {
                TXT_DETENER_ALARMA
            } else {
                getString(R.string.accion_desarmar)
            }
            btnAlarma.backgroundTintList = ColorStateList.valueOf(color(R.color.green_dark))
            btnAlarma.setIconResource(R.drawable.ic_power)
        } else {
            btnAlarma.setText(R.string.alarma_button)
            btnAlarma.backgroundTintList = ColorStateList.valueOf(color(R.color.alarm_primary))
            btnAlarma.setIconResource(R.drawable.ic_clock)
        }
    }

    // =====================================================================

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(this, id)

    private companion object {
        const val PREFS = "funciones"
        const val KEY_ACTIVOS = "horarios_activos"
        const val KEY_HORA_ARMAR = "hora_armar"
        const val KEY_HORA_DESARMAR = "hora_desarmar"
        const val KEY_DIAS = "dias"
        const val TAG_SELECTOR_HORA = "selector_hora"

        const val NUM_BARRAS = 10
        const val VENTANA_MS = 24L * 60 * 60 * 1000
        const val ALTURA_MINIMA = 3

        const val TXT_HORAS_IGUALES = "La hora de armado y la de desarmado no pueden ser iguales"
        const val TXT_PROGRAMACION_APAGADA = "Programación desactivada"
        const val TXT_SIN_DIAS = "Selecciona al menos un día"
        const val TXT_DIA_ACTIVO = "Activo"
        const val TXT_DIA_INACTIVO = "Inactivo"
        const val TXT_SIN_DATOS = "—"
        const val TXT_DETENER_ALARMA = "Detener alarma"
        const val TXT_ALARMA_ACTIVADA = "Alarma activada"
        const val TXT_ALARMA_DETENIDA = "Alarma detenida"
        const val TXT_SISTEMA_DESARMADO = "Sistema desarmado"
    }
}