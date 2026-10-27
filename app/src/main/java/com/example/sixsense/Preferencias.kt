package com.example.sixsense

import android.content.Context
import android.content.SharedPreferences

/**
 * Lee los valores que guarda PantallaAjustes (archivo "ajustes") y guarda el estado
 * armado/desarmado en su propio archivo ("sistema").
 */
object Preferencias {

    private lateinit var ajustes: SharedPreferences
    private lateinit var estado: SharedPreferences

    fun iniciar(context: Context) {
        val app = context.applicationContext
        ajustes = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
        estado = app.getSharedPreferences("sistema", Context.MODE_PRIVATE)
    }

    var armado: Boolean
        get() = estado.getBoolean("armado", false)
        set(v) = estado.edit().putBoolean("armado", v).apply()

    /** Umbral de vibración en g (Ajustes guarda 1..100 = 0.01 g..1.00 g). */
    val umbralVibracionG: Float
        get() = ajustes.getInt("vibracion_minima", 50).coerceIn(1, 100) / 100f

    /** Segundos de cuenta atrás antes de que suene la alarma. */
    val retrasoAlarmaSeg: Int
        get() = ajustes.getInt("retraso_alarma", 10)

    /** false = g, true = m/s². */
    val unidadMs2: Boolean
        get() = ajustes.getString("unidad", "G") == "MS"

    val notifAlertas: Boolean
        get() = ajustes.getBoolean("notif_alertas", true)
}