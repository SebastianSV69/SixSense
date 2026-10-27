package com.example.sixsense

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PantallaFunciones : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_funciones)

        NavegacionInferior.configurar(this, Destino.FUNCIONES)
    }
}