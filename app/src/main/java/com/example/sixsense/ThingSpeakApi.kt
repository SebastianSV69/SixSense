package com.example.sixsense

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Acceso a los canales de ThingSpeak. Las funciones son bloqueantes: llamarlas fuera del hilo principal. */
object ThingSpeakApi {

    private const val BASE = "https://api.thingspeak.com"

    private const val CANAL_SENSORES = "3552169"
    private const val CANAL_ACTUADORES = "3552171"
    private const val READ_KEY_SENSORES = "TQ2DW1P0EWQE10YZ"
    private const val READ_KEY_ACTUADORES = "8615DX6MX28WKUT7"
    private const val WRITE_KEY_ACTUADORES = "VKZK1K62FWYV0SJX"

    /** Una entrada del canal Sensores. field1 = magnético (1 abierto, 0 cerrado), field2 = vibración en g. */
    class Lectura(
        val entryId: Long,
        val creadoMillis: Long,
        val magnetico: Int?,
        val vibracionG: Double?,
    )

    /** [lecturas] viene ordenada de la más antigua a la más reciente. */
    class RespuestaSensores(val lecturas: List<Lectura>, val servidorMillis: Long)

    private class Respuesta(val codigo: Int, val cuerpo: String, val fechaServidor: Long)

    /** Las últimas [resultados] entradas del canal Sensores. */
    fun leerSensores(resultados: Int = 20): RespuestaSensores = pedirSensores("results=$resultados")

    /** Todas las entradas de las últimas 24 horas (hasta 8000). */
    fun leerSensoresUltimas24h(): RespuestaSensores = pedirSensores("days=1&results=8000")

    /** true = alarma encendida, false = apagada, null = el canal aún no tiene datos. */
    fun leerAlarma(): Boolean? {
        val r = get(
            "$BASE/channels/$CANAL_ACTUADORES/fields/1/last.json?api_key=$READ_KEY_ACTUADORES"
        )
        if (r.codigo != 200) throw IOException("Actuadores: HTTP ${r.codigo}")
        return try {
            numero(JSONObject(r.cuerpo), "field1")?.let { it >= 0.5 }
        } catch (e: JSONException) {
            null // canal vacío: ThingSpeak responde "-1"
        }
    }

    /** Escribe el campo Alarma. Devuelve true si ThingSpeak aceptó el dato. */
    fun escribirAlarma(activa: Boolean): Boolean {
        val valor = if (activa) 1 else 0
        val r = get("$BASE/update?api_key=$WRITE_KEY_ACTUADORES&field1=$valor")
        // ThingSpeak responde el id de la entrada, o "0" si rechazó el dato (límite de 15 s)
        return r.codigo == 200 && (r.cuerpo.trim().toLongOrNull() ?: 0L) > 0L
    }

    // ---------- Internos ----------

    private fun pedirSensores(parametros: String): RespuestaSensores {
        val r = get(
            "$BASE/channels/$CANAL_SENSORES/feeds.json?api_key=$READ_KEY_SENSORES&$parametros"
        )
        if (r.codigo != 200) throw IOException("Sensores: HTTP ${r.codigo}")

        val feeds = try {
            JSONObject(r.cuerpo).optJSONArray("feeds") ?: JSONArray()
        } catch (e: JSONException) {
            throw IOException("Sensores: respuesta inválida")
        }

        val lecturas = (0 until feeds.length()).map { i ->
            val f = feeds.getJSONObject(i)
            Lectura(
                entryId = f.optLong("entry_id"),
                creadoMillis = parseFecha(f.optString("created_at")),
                magnetico = numero(f, "field1")?.let { if (it >= 0.5) 1 else 0 },
                vibracionG = numero(f, "field2"),
            )
        }.sortedBy { it.entryId }

        val servidor = if (r.fechaServidor > 0) r.fechaServidor else System.currentTimeMillis()
        return RespuestaSensores(lecturas, servidor)
    }

    private fun get(url: String): Respuesta {
        val conexion = URL(url).openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 8000
            conexion.readTimeout = 8000
            conexion.requestMethod = "GET"
            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val cuerpo = flujo?.bufferedReader()?.use { it.readText() } ?: ""
            return Respuesta(codigo, cuerpo, conexion.date)
        } finally {
            conexion.disconnect()
        }
    }

    private fun numero(obj: JSONObject, campo: String): Double? =
        if (obj.isNull(campo)) null else obj.optString(campo).toDoubleOrNull()

    private fun parseFecha(texto: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(texto)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}