package com.example.sixsense

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

enum class EstadoConexion { CONECTADO, EN_ESPERA, SIN_CONEXION }
enum class TipoIntrusion { APERTURA, CONFIRMADA }

fun interface OyenteSistema {
    fun alCambiar()
}

/**
 * Estado y lógica de seguridad compartidos por todas las pantallas.
 * Consulta ThingSpeak cada 15 s mientras la app esté abierta.
 *
 * Reglas (solo con el sistema armado):
 *  - Vibración sobre el umbral de Ajustes -> notificación preventiva.
 *  - Puerta abierta -> cuenta atrás (retraso de Ajustes) y luego alarma.
 *    Si hubo vibración ±30 s alrededor de la apertura, es "Intrusión confirmada".
 *  - La cuenta atrás y la alarma automática solo se detienen desarmando el sistema.
 */
object SistemaSeguridad {

    private const val TAG = "SixSense"
    private const val INTERVALO_CONSULTA_MS = 15_000L
    private const val VENTANA_VIBRACION_MS = 30_000L
    private const val DATOS_VIGENTES_MS = 5 * 60_000L
    private const val REINTENTO_ESCRITURA_MS = 16_000L // ThingSpeak: 1 escritura cada 15 s
    private const val IGNORAR_LECTURA_ALARMA_MS = 20_000L

    private val main = Handler(Looper.getMainLooper())
    private val red = Executors.newSingleThreadExecutor()
    private lateinit var app: Context
    private var iniciado = false
    private val oyentes = mutableListOf<OyenteSistema>()

    /** Se invoca cuando empieza una cuenta atrás (SixSenseApp abre Notificaciones). */
    var alIniciarCuentaAtras: (() -> Unit)? = null

    // ---------- Estado público (solo lectura) ----------

    var armado = false
        private set
    var conexion = EstadoConexion.EN_ESPERA
        private set
    var magneticoAbierto: Boolean? = null
        private set
    var magneticoMillis = 0L
        private set
    var vibracionG: Double? = null
        private set
    var vibracionMillis = 0L
        private set
    var alarmaActiva = false
        private set
    /** true si la alarma activa fue disparada a mano desde Funciones. */
    var alarmaManual = false
        private set
    /** Segundos restantes de la cuenta atrás, o null si no hay. */
    var cuentaAtras: Int? = null
        private set
    var tipoIntrusion: TipoIntrusion? = null
        private set

    /** Hay una cuenta atrás o una alarma automática: solo se sale desarmando el sistema. */
    val enAlerta: Boolean
        get() = cuentaAtras != null || (alarmaActiva && !alarmaManual)

    // ---------- Estado interno ----------

    private var activos = 0
    private var consultando = false
    private var ultimoEntryId = -1L
    private var offsetServidor = 0L
    private var ultimaVibracionMillis = 0L
    private var inicioIntrusionMillis = 0L
    private var finCuentaAtras = 0L
    private var alarmaPendiente: Boolean? = null
    private var escribiendo = false
    private var ignorarLecturaAlarmaHasta = 0L
    private var sirena: Ringtone? = null

    // ---------- Inicio ----------

    fun iniciar(context: Context) {
        if (iniciado) return
        iniciado = true
        app = context.applicationContext
        Preferencias.iniciar(app)
        HistorialEventos.iniciar(app)
        Avisos.crearCanal(app)
        armado = Preferencias.armado
    }

    fun agregarOyente(oyente: OyenteSistema) {
        if (oyente !in oyentes) oyentes.add(oyente)
    }

    fun quitarOyente(oyente: OyenteSistema) {
        oyentes.remove(oyente)
    }

    private fun notificar() {
        actualizarSirena()
        oyentes.toList().forEach { it.alCambiar() }
    }

    // ---------- Ciclo de vida de la app ----------

    fun activityIniciada() {
        activos++
        if (activos == 1) {
            consultarAhora()
            main.removeCallbacks(tickConsulta)
            main.postDelayed(tickConsulta, INTERVALO_CONSULTA_MS)
        }
    }

    fun activityDetenida() {
        activos = max(0, activos - 1)
        if (activos == 0) main.removeCallbacks(tickConsulta)
    }

    private val tickConsulta = object : Runnable {
        override fun run() {
            consultarAhora()
            main.postDelayed(this, INTERVALO_CONSULTA_MS)
        }
    }

    // ---------- Consulta a ThingSpeak ----------

    fun consultarAhora() {
        if (consultando) return
        consultando = true
        red.execute {
            var sensores: ThingSpeakApi.RespuestaSensores? = null
            var alarma: Boolean? = null
            var error: Exception? = null
            try {
                sensores = ThingSpeakApi.leerSensores()
                alarma = ThingSpeakApi.leerAlarma()
            } catch (e: Exception) {
                error = e
            }
            main.post {
                consultando = false
                alResponder(sensores, alarma, error)
            }
        }
    }

    /** Lecturas de las últimas 24 h para la gráfica de Funciones (null si falló la consulta). */
    fun consultarUltimas24h(alTerminar: (List<ThingSpeakApi.Lectura>?) -> Unit) {
        red.execute {
            val lecturas = try {
                ThingSpeakApi.leerSensoresUltimas24h().lecturas
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo leer el historial de 24 h", e)
                null
            }
            main.post { alTerminar(lecturas) }
        }
    }

    private fun alResponder(
        sensores: ThingSpeakApi.RespuestaSensores?,
        alarma: Boolean?,
        error: Exception?,
    ) {
        if (error != null || sensores == null) {
            Log.w(TAG, "No se pudo consultar ThingSpeak", error)
            conexion = EstadoConexion.SIN_CONEXION
            notificar()
            return
        }

        // Reloj de ThingSpeak: así las edades no dependen de la hora del emulador
        offsetServidor = sensores.servidorMillis - System.currentTimeMillis()

        // Última lectura conocida de cada sensor (una entrada puede traer un campo vacío)
        sensores.lecturas.lastOrNull { it.magnetico != null }?.let {
            magneticoAbierto = it.magnetico == 1
            magneticoMillis = it.creadoMillis
        }
        sensores.lecturas.lastOrNull { it.vibracionG != null }?.let {
            vibracionG = it.vibracionG
            vibracionMillis = it.creadoMillis
        }

        // Estado real de la alarma en ThingSpeak (si no hay una escritura en curso)
        if (alarma != null && alarmaPendiente == null && !escribiendo &&
            SystemClock.elapsedRealtime() >= ignorarLecturaAlarmaHasta
        ) {
            alarmaActiva = alarma
            if (!alarma) alarmaManual = false
        }

        val ultima = max(magneticoMillis, vibracionMillis)
        conexion = if (ultima == 0L || ahoraServidor() - ultima > DATOS_VIGENTES_MS) {
            EstadoConexion.EN_ESPERA
        } else {
            EstadoConexion.CONECTADO
        }

        procesarNuevas(sensores.lecturas)
        if (alarmaPendiente != null) intentarEscritura()
        notificar()
    }

    // ---------- Reglas ----------

    private fun procesarNuevas(lecturas: List<ThingSpeakApi.Lectura>) {
        if (ultimoEntryId < 0) {
            // Primera consulta: lo anterior es historia, solo se toma como referencia
            ultimoEntryId = lecturas.maxOfOrNull { it.entryId } ?: 0L
            return
        }
        for (lectura in lecturas) {
            if (lectura.entryId <= ultimoEntryId) continue
            ultimoEntryId = lectura.entryId
            evaluar(lectura)
        }
    }

    private fun evaluar(l: ThingSpeakApi.Lectura) {
        if (!armado) return
        val vib = l.vibracionG
        if (vib != null && vib >= Preferencias.umbralVibracionG) alVibrar(l, vib)
        if (l.magnetico == 1) alAbrirse(l)
    }

    private fun alVibrar(l: ThingSpeakApi.Lectura, vib: Double) {
        ultimaVibracionMillis = l.creadoMillis
        val cuenta = cuentaAtras
        when {
            cuenta != null -> {
                // La puerta ya se abrió: si la vibración llega dentro de la ventana, se confirma
                if (tipoIntrusion == TipoIntrusion.APERTURA &&
                    abs(l.creadoMillis - inicioIntrusionMillis) <= VENTANA_VIBRACION_MS
                ) {
                    tipoIntrusion = TipoIntrusion.CONFIRMADA
                    registrar(
                        TipoEvento.INTRUSION,
                        t(R.string.ev_intrusion_titulo),
                        t(R.string.ev_intrusion_posterior_detalle, cuenta)
                    )
                }
            }
            alarmaActiva -> Unit
            else -> registrar(
                TipoEvento.VIBRACION,
                t(R.string.ev_vibracion_titulo),
                t(R.string.ev_vibracion_detalle, formatoVibracion(vib))
            )
        }
    }

    private fun alAbrirse(l: ThingSpeakApi.Lectura) {
        if (cuentaAtras != null || alarmaActiva) return

        val confirmada = ultimaVibracionMillis > 0 &&
                (l.creadoMillis - ultimaVibracionMillis) in 0..VENTANA_VIBRACION_MS
        val segundos = Preferencias.retrasoAlarmaSeg

        tipoIntrusion = if (confirmada) TipoIntrusion.CONFIRMADA else TipoIntrusion.APERTURA
        inicioIntrusionMillis = l.creadoMillis

        if (confirmada) {
            registrar(
                TipoEvento.INTRUSION,
                t(R.string.ev_intrusion_titulo),
                t(R.string.ev_intrusion_detalle, segundos)
            )
        } else {
            registrar(
                TipoEvento.APERTURA,
                t(R.string.ev_apertura_titulo),
                t(R.string.ev_apertura_detalle, segundos)
            )
        }
        iniciarCuentaAtras(segundos)
    }

    // ---------- Cuenta atrás ----------

    private val tickCuenta = object : Runnable {
        override fun run() {
            if (cuentaAtras == null) return
            val restanteMs = finCuentaAtras - SystemClock.elapsedRealtime()
            if (restanteMs <= 0) {
                dispararAlarma()
                return
            }
            cuentaAtras = ceil(restanteMs / 1000.0).toInt()
            notificar()
            main.postDelayed(this, 1000)
        }
    }

    private fun iniciarCuentaAtras(segundos: Int) {
        finCuentaAtras = SystemClock.elapsedRealtime() + segundos * 1000L
        cuentaAtras = segundos
        main.removeCallbacks(tickCuenta)
        main.post(tickCuenta)
        alIniciarCuentaAtras?.invoke()
    }

    private fun cancelarCuentaAtras() {
        main.removeCallbacks(tickCuenta)
        cuentaAtras = null
    }

    private fun dispararAlarma() {
        cancelarCuentaAtras()
        alarmaActiva = true
        alarmaManual = false
        registrar(TipoEvento.ALARMA, t(R.string.ev_alarma_titulo), t(R.string.ev_alarma_detalle))
        solicitarAlarma(true)
        notificar()
    }

    // ---------- Acciones del usuario ----------

    fun alternarArmado() {
        if (armado) desarmar() else armar()
    }

    fun armar() {
        if (armado) return
        armado = true
        Preferencias.armado = true
        ultimaVibracionMillis = 0L
        notificar()
    }

    /** Cancela la cuenta atrás y apaga la alarma si estaba sonando. */
    fun desarmar() {
        val habiaAlerta = cuentaAtras != null || alarmaActiva
        armado = false
        Preferencias.armado = false
        cancelarCuentaAtras()
        tipoIntrusion = null
        if (alarmaActiva || alarmaPendiente == true) {
            alarmaActiva = false
            alarmaManual = false
            solicitarAlarma(false)
        }
        if (habiaAlerta) {
            registrar(
                TipoEvento.DESARME,
                t(R.string.ev_desarme_titulo),
                t(R.string.ev_desarme_detalle),
                aviso = false
            )
        }
        notificar()
    }

    /** Alarma manual (pantalla Funciones). */
    fun activarAlarmaManual() {
        if (alarmaActiva) return
        cancelarCuentaAtras()
        alarmaActiva = true
        alarmaManual = true
        registrar(TipoEvento.MANUAL, t(R.string.ev_manual_titulo), t(R.string.ev_manual_detalle))
        solicitarAlarma(true)
        notificar()
    }

    /** Apaga la alarma manual sin cambiar el estado armado. */
    fun detenerAlarma() {
        if (!alarmaActiva) return
        alarmaActiva = false
        alarmaManual = false
        tipoIntrusion = null
        registrar(
            TipoEvento.DESARME,
            t(R.string.ev_detenida_titulo),
            t(R.string.ev_detenida_detalle),
            aviso = false
        )
        solicitarAlarma(false)
        notificar()
    }

    /** Botón de Funciones: la alarma manual se detiene; una automática exige desarmar. */
    fun detenerAlarmaActual() {
        if (alarmaManual) detenerAlarma() else desarmar()
    }

    // ---------- Escritura en Actuadores ----------

    private val reintentoEscritura = Runnable { intentarEscritura() }

    private fun solicitarAlarma(activa: Boolean) {
        alarmaPendiente = activa
        intentarEscritura()
    }

    private fun intentarEscritura() {
        val valor = alarmaPendiente ?: return
        if (escribiendo) return
        escribiendo = true
        red.execute {
            val ok = try {
                ThingSpeakApi.escribirAlarma(valor)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo escribir la alarma", e)
                false
            }
            main.post {
                escribiendo = false
                if (ok) {
                    if (alarmaPendiente == valor) alarmaPendiente = null
                    ignorarLecturaAlarmaHasta = SystemClock.elapsedRealtime() + IGNORAR_LECTURA_ALARMA_MS
                }
                // Si falló (por ejemplo, por el límite de 15 s) o el valor cambió, se reintenta
                if (alarmaPendiente != null) {
                    main.removeCallbacks(reintentoEscritura)
                    main.postDelayed(reintentoEscritura, REINTENTO_ESCRITURA_MS)
                }
                notificar()
            }
        }
    }

    // ---------- Sirena del teléfono ----------

    /** Suena mientras la alarma esté activa (manual o automática). */
    private fun actualizarSirena() {
        if (alarmaActiva) {
            if (sirena?.isPlaying == true) return
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            sirena = RingtoneManager.getRingtone(app, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                // Repetir el sonido solo se puede pedir desde Android 9
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        } else {
            sirena?.stop()
            sirena = null
        }
    }

    // ---------- Historial y avisos ----------

    private fun registrar(tipo: TipoEvento, titulo: String, detalle: String, aviso: Boolean = true) {
        HistorialEventos.agregar(Evento(tipo, titulo, detalle, System.currentTimeMillis()))
        if (aviso && Preferencias.notifAlertas) Avisos.mostrar(app, titulo, detalle)
    }

    // ---------- Utilidades para las pantallas ----------

    fun ahoraServidor(): Long = System.currentTimeMillis() + offsetServidor

    /** "12 s", "3 min" o "2 h" desde la hora de ThingSpeak indicada. */
    fun edadCorta(creadoMillis: Long): String {
        val seg = max(0L, (ahoraServidor() - creadoMillis) / 1000).toInt()
        return when {
            seg < 60 -> app.getString(R.string.edad_segundos, seg)
            seg < 3600 -> app.getString(R.string.edad_minutos, seg / 60)
            else -> app.getString(R.string.edad_horas, seg / 3600)
        }
    }

    fun haceTexto(creadoMillis: Long): String =
        app.getString(R.string.hace_formato, edadCorta(creadoMillis))

    /** Vibración en la unidad elegida en Ajustes. */
    fun formatoVibracion(g: Double): String {
        val ms2 = Preferencias.unidadMs2
        val valor = if (ms2) g * 9.80665 else g
        val unidad = app.getString(if (ms2) R.string.unidad_ms else R.string.unidad_g)
        return String.format(Locale.getDefault(), "%.2f %s", valor, unidad)
    }

    private fun t(id: Int, vararg args: Any): String = app.getString(id, *args)
}