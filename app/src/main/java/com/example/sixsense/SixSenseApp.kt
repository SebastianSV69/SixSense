package com.example.sixsense

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Intent
import android.os.Bundle

class SixSenseApp : Application() {

    /** Pantalla visible en este momento (null si la app está en segundo plano). */
    private var actual: Activity? = null

    override fun onCreate() {
        super.onCreate()
        SistemaSeguridad.iniciar(this)

        // Al empezar la cuenta atrás, la app lleva al usuario a Notificaciones
        SistemaSeguridad.alIniciarCuentaAtras = { abrirNotificacionesSiCorresponde() }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            // La consulta a ThingSpeak solo corre mientras la app está abierta
            override fun onActivityStarted(activity: Activity) = SistemaSeguridad.activityIniciada()
            override fun onActivityStopped(activity: Activity) = SistemaSeguridad.activityDetenida()
            override fun onActivityResumed(activity: Activity) {
                actual = activity
            }

            override fun onActivityPaused(activity: Activity) {
                if (actual === activity) actual = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun abrirNotificacionesSiCorresponde() {
        val pantalla = actual ?: return // en segundo plano queda la notificación del sistema
        if (pantalla is PantallaBloqueo || pantalla is PantallaNotificaciones) return
        pantalla.startActivity(
            Intent(pantalla, PantallaNotificaciones::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }
}