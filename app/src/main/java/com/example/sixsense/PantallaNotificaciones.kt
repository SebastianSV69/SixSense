package com.example.sixsense

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PantallaNotificaciones : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_notificaciones)

        setupNavigation()
    }

    private fun setupNavigation() {
        findViewById<TextView>(R.id.navInicio)?.setOnClickListener {
            startActivity(Intent(this, PantallaPrincipal::class.java))
            finish()
        }
        findViewById<TextView>(R.id.navDispositivos)?.setOnClickListener {
            startActivity(Intent(this, PantallaDispositivos::class.java))
            finish()
        }
        findViewById<TextView>(R.id.navFunciones)?.setOnClickListener {
            startActivity(Intent(this, PantallaFunciones::class.java))
            finish()
        }
        findViewById<TextView>(R.id.navAjustes)?.setOnClickListener {
            startActivity(Intent(this, PantallaAjustes::class.java))
            finish()
        }
        findViewById<TextView>(R.id.navBloquear)?.setOnClickListener {
            startActivity(Intent(this, PantallaBloqueo::class.java))
            finish()
        }
    }
}