package com.example.sixsense

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class TipoEvento { VIBRACION, APERTURA, INTRUSION, ALARMA, MANUAL, DESARME }

class Evento(
    val tipo: TipoEvento,
    val titulo: String,
    val detalle: String,
    val millis: Long,
)

/** Historial de eventos guardado en el teléfono (los 100 más recientes). */
object HistorialEventos {

    private const val MAXIMO = 100
    private const val CLAVE = "eventos"
    private lateinit var sp: SharedPreferences

    fun iniciar(context: Context) {
        sp = context.applicationContext.getSharedPreferences("sixsense_historial", Context.MODE_PRIVATE)
    }

    /** Del más reciente al más antiguo. */
    fun lista(): List<Evento> {
        val texto = sp.getString(CLAVE, null) ?: return emptyList()
        return try {
            val arreglo = JSONArray(texto)
            (0 until arreglo.length()).map { i ->
                val o = arreglo.getJSONObject(i)
                Evento(
                    TipoEvento.valueOf(o.getString("tipo")),
                    o.getString("titulo"),
                    o.getString("detalle"),
                    o.getLong("millis"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun agregar(evento: Evento) {
        val actual = lista().toMutableList()
        actual.add(0, evento)
        val arreglo = JSONArray()
        actual.take(MAXIMO).forEach {
            arreglo.put(
                JSONObject()
                    .put("tipo", it.tipo.name)
                    .put("titulo", it.titulo)
                    .put("detalle", it.detalle)
                    .put("millis", it.millis)
            )
        }
        sp.edit().putString(CLAVE, arreglo.toString()).apply()
    }

    fun limpiar() {
        sp.edit().remove(CLAVE).apply()
    }
}