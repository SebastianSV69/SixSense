package com.example.sixsense

import android.app.Activity
import android.content.Intent
import android.view.View

/** Pantallas que tienen ítem propio en la barra de navegación inferior. */
enum class Destino(val idNav: Int, val pantalla: Class<out Activity>) {
    INICIO(R.id.navInicio, PantallaPrincipal::class.java),
    DISPOSITIVOS(R.id.navDispositivos, PantallaDispositivos::class.java),
    FUNCIONES(R.id.navFunciones, PantallaFunciones::class.java),
    AJUSTES(R.id.navAjustes, PantallaAjustes::class.java),
}

object NavegacionInferior {

    /**
     * Configura la barra compartida (include_barra_navegacion).
     * [actual] queda resaltado y sin acción; pasa null si la pantalla
     * no tiene ítem en la barra (por ejemplo, Notificaciones).
     */
    fun configurar(activity: Activity, actual: Destino?) {
        Destino.values().forEach { destino ->
            val item = activity.findViewById<View>(destino.idNav) ?: return@forEach
            item.isSelected = destino == actual
            if (destino != actual) {
                item.setOnClickListener { abrir(activity, destino.pantalla) }
            }
        }

        activity.findViewById<View>(R.id.navBloquear)?.setOnClickListener {
            activity.startActivity(
                Intent(activity, PantallaBloqueo::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }

    fun abrirNotificaciones(activity: Activity) {
        abrir(activity, PantallaNotificaciones::class.java)
    }

    // REORDER_TO_FRONT conserva el estado de las pantallas ya abiertas
    private fun abrir(activity: Activity, pantalla: Class<out Activity>) {
        activity.startActivity(
            Intent(activity, pantalla).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }
}