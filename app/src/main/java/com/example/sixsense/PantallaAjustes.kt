package com.example.sixsense

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PantallaAjustes : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_ajustes)

        NavegacionInferior.configurar(this, Destino.AJUSTES)
    }
}