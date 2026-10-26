package com.example.sixsense

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged

/**
 * Pantalla de bloqueo: PIN de 4 dígitos o contraseña (mínimo 6 caracteres),
 * ambos en esta misma pantalla.
 */
class PantallaBloqueo : AppCompatActivity() {

    private val pin = StringBuilder()
    private var modoPin = true

    private lateinit var tvInstruccion: TextView
    private lateinit var panelPin: View
    private lateinit var panelContrasena: View
    private lateinit var contenedorPuntos: View
    private lateinit var etContrasena: EditText
    private lateinit var btnDesbloquear: View
    private lateinit var btnAlternarModo: TextView
    private lateinit var puntos: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        // El diseño es claro: íconos oscuros en las barras del sistema
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_bloqueo)

        tvInstruccion = findViewById(R.id.tvInstruccion)
        panelPin = findViewById(R.id.panelPin)
        panelContrasena = findViewById(R.id.panelContrasena)
        contenedorPuntos = findViewById(R.id.contenedorPuntos)
        etContrasena = findViewById(R.id.etContrasena)
        btnDesbloquear = findViewById(R.id.btnDesbloquear)
        btnAlternarModo = findViewById(R.id.btnAlternarModo)
        puntos = listOf(
            findViewById(R.id.punto1), findViewById(R.id.punto2),
            findViewById(R.id.punto3), findViewById(R.id.punto4)
        )

        ajustarBordesDelSistema(findViewById(R.id.raiz))

        findViewById<TextView>(R.id.tvSaludo).text = getString(R.string.bloqueo_saludo, NOMBRE_USUARIO)

        configurarTeclado()
        configurarContrasena()

        btnAlternarModo.setOnClickListener { alternarModo() }
        btnDesbloquear.setOnClickListener { desbloquear() }

        actualizarPuntos()
        actualizarBoton()
    }

    /** Respeta barra de estado, barra de navegación, notch y teclado. */
    private fun ajustarBordesDelSistema(raiz: View) {
        val baseSuperior = raiz.paddingTop
        val baseInferior = raiz.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )
            vista.updatePadding(top = baseSuperior + barras.top, bottom = baseInferior + barras.bottom)
            insets
        }
    }

    // ---------- PIN ----------

    private fun configurarTeclado() {
        val teclas = listOf(
            R.id.tecla0, R.id.tecla1, R.id.tecla2, R.id.tecla3, R.id.tecla4,
            R.id.tecla5, R.id.tecla6, R.id.tecla7, R.id.tecla8, R.id.tecla9
        )
        teclas.forEachIndexed { digito, id ->
            findViewById<View>(id).setOnClickListener { agregarDigito(digito) }
        }

        val teclaBorrar = findViewById<View>(R.id.teclaBorrar)
        teclaBorrar.setOnClickListener { borrarUltimoDigito() }
        // Mantener presionado borra todo el PIN
        teclaBorrar.setOnLongClickListener {
            pin.clear()
            actualizarPuntos()
            actualizarBoton()
            true
        }
    }

    private fun agregarDigito(digito: Int) {
        if (pin.length >= LONGITUD_PIN) return
        pin.append(digito)
        actualizarPuntos()
        actualizarBoton()
    }

    /** Borra de derecha a izquierda: 1234 -> 123 -> 12 ... */
    private fun borrarUltimoDigito() {
        if (pin.isEmpty()) return
        pin.deleteCharAt(pin.length - 1)
        actualizarPuntos()
        actualizarBoton()
    }

    private fun actualizarPuntos() {
        puntos.forEachIndexed { indice, punto -> punto.isSelected = indice < pin.length }
        contenedorPuntos.contentDescription =
            getString(R.string.bloqueo_pin_descripcion, pin.length, LONGITUD_PIN)
    }

    // ---------- Contraseña ----------

    private fun configurarContrasena() {
        etContrasena.doAfterTextChanged { actualizarBoton() }
        etContrasena.setOnEditorActionListener { _, _, _ ->
            desbloquear()
            true
        }
    }

    private fun alternarModo() {
        modoPin = !modoPin

        // Al cambiar de método se limpian ambos campos
        pin.clear()
        etContrasena.text?.clear()

        panelPin.isVisible = modoPin
        panelContrasena.isVisible = !modoPin
        tvInstruccion.setText(
            if (modoPin) R.string.bloqueo_instruccion_pin else R.string.bloqueo_instruccion_contrasena
        )
        btnAlternarModo.setText(
            if (modoPin) R.string.bloqueo_usar_contrasena else R.string.bloqueo_usar_pin
        )

        val teclado = WindowInsetsControllerCompat(window, etContrasena)
        if (modoPin) {
            etContrasena.clearFocus()
            teclado.hide(WindowInsetsCompat.Type.ime())
        } else {
            etContrasena.requestFocus()
            teclado.show(WindowInsetsCompat.Type.ime())
        }

        actualizarPuntos()
        actualizarBoton()
    }

    // ---------- Desbloqueo ----------

    /** El botón se ilumina con 4 dígitos (PIN) o 6 o más caracteres (contraseña). */
    private fun credencialCompleta(): Boolean =
        if (modoPin) pin.length == LONGITUD_PIN
        else (etContrasena.text?.length ?: 0) >= LONGITUD_MINIMA_CONTRASENA

    private fun actualizarBoton() {
        btnDesbloquear.isEnabled = credencialCompleta()
    }

    private fun desbloquear() {
        if (!credencialCompleta()) return
        // TODO: validar el PIN / contraseña reales (por ahora cualquier credencial completa pasa)
        startActivity(Intent(this, PantallaPrincipal::class.java))
        finish()
    }

    private companion object {
        const val NOMBRE_USUARIO = "Alex"   // TODO: tomar del usuario registrado
        const val LONGITUD_PIN = 4
        const val LONGITUD_MINIMA_CONTRASENA = 6
    }
}