package com.example.sixsense

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PantallaPrincipal : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_principal)

        setupNavigation()
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.btnNotificaciones)?.setOnClickListener {
            startActivity(Intent(this, PantallaNotificaciones::class.java))
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