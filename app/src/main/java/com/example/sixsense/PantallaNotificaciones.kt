package com.example.sixsense

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PantallaNotificaciones : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_notificaciones)

        // No tiene ítem propio en la barra: ninguno queda resaltado
        NavegacionInferior.configurar(this, null)
    }
}