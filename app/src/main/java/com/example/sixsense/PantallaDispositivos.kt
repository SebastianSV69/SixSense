package com.example.sixsense

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import kotlin.math.max

class PantallaDispositivos : AppCompatActivity() {

    // ---------- Modelo (solo visual, sin persistencia) ----------

    private enum class Tipo(
        @StringRes val nombre: Int,
        @StringRes val rol: Int,
        @DrawableRes val icono: Int,
    ) {
        MAGNETICO(R.string.tipo_sensor_magnetico, R.string.tipo_sensor_magnetico, R.drawable.ic_magnet),
        VIBRACION(R.string.tipo_sensor_vibracion, R.string.tipo_sensor_vibracion, R.drawable.ic_vibration),
        ALARMA(R.string.tipo_alarma_sonora, R.string.tipo_alarma_sonora, R.drawable.ic_siren),
        ARDUINO(R.string.tipo_arduino_uno, R.string.rol_controlador_central, R.drawable.ic_chip),
    }

    private enum class Conexion(@StringRes val nombre: Int) {
        CABLEADO(R.string.conexion_cableado),
        USB(R.string.conexion_usb),
        BLUETOOTH(R.string.conexion_bluetooth),
        WIFI(R.string.conexion_wifi),
    }

    /** El tipo es inmutable; nombre, ubicación y conexión se pueden editar. */
    private class Dispositivo(
        val tipo: Tipo,
        var nombre: String,
        var ubicacion: String,
        var conexion: Conexion,
    )

    private val dispositivos = mutableListOf(
        Dispositivo(Tipo.ARDUINO, "Arduino Uno", "Sala principal", Conexion.USB),
        Dispositivo(Tipo.MAGNETICO, "Sensor puerta", "Entrada principal", Conexion.CABLEADO),
        Dispositivo(Tipo.VIBRACION, "Sensor ventana", "Habitación", Conexion.CABLEADO),
        Dispositivo(Tipo.ALARMA, "Alarma principal", "Pasillo", Conexion.CABLEADO),
    )

    // ---------- Estado de la hoja ----------

    private var hojaAbierta = false
    private var paso = 1
    private var conexionElegida: Conexion? = null

    /** null = vinculando uno nuevo; distinto de null = editando ese dispositivo. */
    private var editando: Dispositivo? = null

    // ---------- Vistas ----------

    private lateinit var scroll: NestedScrollView
    private lateinit var barra: View
    private lateinit var tvContador: TextView
    private lateinit var tvChipRed: TextView
    private lateinit var lista: LinearLayout

    private lateinit var scrim: View
    private lateinit var hoja: View
    private lateinit var tvPaso: TextView
    private lateinit var tvTituloHoja: TextView
    private lateinit var segmento2: View
    private lateinit var pasoDatos: View
    private lateinit var pasoConexion: View
    private lateinit var etNombre: EditText
    private lateinit var etUbicacion: EditText
    private lateinit var spTipo: Spinner
    private lateinit var tvTipoBloqueado: TextView
    private lateinit var btnContinuar: View
    private lateinit var btnEliminar: View
    private lateinit var tvSubtituloConexion: TextView
    private lateinit var btnVincular: TextView
    private lateinit var opciones: Map<Conexion, View>

    private val atrasHoja = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (paso == 2) mostrarPaso(1) else cerrarHoja()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pantalla_dispositivos)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        enlazarVistas()
        ajustarBordesDelSistema()
        configurarBarraNavegacion()
        configurarSpinner()
        configurarHoja()
        onBackPressedDispatcher.addCallback(this, atrasHoja)

        renderLista()
    }

    private fun enlazarVistas() {
        scroll = findViewById(R.id.scrollDispositivos)
        barra = findViewById(R.id.barraNavegacion)
        tvContador = findViewById(R.id.tvContador)
        tvChipRed = findViewById(R.id.tvChipRed)
        lista = findViewById(R.id.listaDispositivos)

        scrim = findViewById(R.id.scrim)
        hoja = findViewById(R.id.hojaVincular)
        tvPaso = findViewById(R.id.tvPaso)
        tvTituloHoja = findViewById(R.id.tvTituloHoja)
        segmento2 = findViewById(R.id.segmento2)
        pasoDatos = findViewById(R.id.pasoDatos)
        pasoConexion = findViewById(R.id.pasoConexion)
        etNombre = findViewById(R.id.etNombre)
        etUbicacion = findViewById(R.id.etUbicacion)
        spTipo = findViewById(R.id.spTipo)
        tvTipoBloqueado = findViewById(R.id.tvTipoBloqueado)
        btnContinuar = findViewById(R.id.btnContinuar)
        btnEliminar = findViewById(R.id.btnEliminar)
        tvSubtituloConexion = findViewById(R.id.tvSubtituloConexion)
        btnVincular = findViewById(R.id.btnVincular)
        opciones = mapOf(
            Conexion.CABLEADO to findViewById(R.id.opcCableado),
            Conexion.USB to findViewById(R.id.opcUsb),
            Conexion.BLUETOOTH to findViewById(R.id.opcBluetooth),
            Conexion.WIFI to findViewById(R.id.opcWifi),
        )
    }

    // ---------- Configuración ----------

    /** Respeta barra de estado, notch, barra del sistema y teclado. */
    private fun ajustarBordesDelSistema() {
        val raiz = findViewById<View>(R.id.raiz)
        val baseScroll = scroll.paddingTop
        val baseBarra = barra.paddingBottom
        val baseHoja = hoja.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { _, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            scroll.updatePadding(top = baseScroll + b.top)
            barra.updatePadding(bottom = baseBarra + b.bottom)
            hoja.updatePadding(bottom = baseHoja + max(b.bottom, ime.bottom))
            insets
        }
    }

    private fun configurarBarraNavegacion() {
        findViewById<View>(R.id.navDispositivos).isSelected = true

        findViewById<View>(R.id.navInicio).setOnClickListener {
            startActivity(
                Intent(this, PantallaPrincipal::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
        findViewById<View>(R.id.navFunciones).setOnClickListener { avisoProximamente() }
        findViewById<View>(R.id.navAjustes).setOnClickListener { avisoProximamente() }
        findViewById<View>(R.id.navBloquear).setOnClickListener {
            startActivity(
                Intent(this, PantallaBloqueo::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }

    private fun configurarSpinner() {
        val nombres = Tipo.values().map { getString(it.nombre) }
        val adaptador = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, nombres
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).also {
                    estilizarItem(it, 0, 0)
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).also {
                    estilizarItem(it, 16.dp(), 12.dp())
                }
        }
        adaptador.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spTipo.adapter = adaptador
    }

    private fun estilizarItem(tv: TextView, horizontal: Int, vertical: Int) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        tv.setTextColor(color(R.color.texto_principal))
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER_VERTICAL
        tv.setPadding(horizontal, vertical, horizontal, vertical)
    }

    private fun configurarHoja() {
        findViewById<View>(R.id.btnVincularNuevo).setOnClickListener { abrirHoja() }
        findViewById<View>(R.id.btnCerrarHoja).setOnClickListener { cerrarHoja() }
        scrim.setOnClickListener { cerrarHoja() }

        // Paso 1: "Continuar" se habilita cuando Nombre y Ubicación tienen texto
        etNombre.doAfterTextChanged { validarDatos() }
        etUbicacion.doAfterTextChanged { validarDatos() }
        etUbicacion.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE && btnContinuar.isEnabled) {
                irAlPaso2()
                true
            } else false
        }
        btnContinuar.setOnClickListener { irAlPaso2() }
        btnEliminar.setOnClickListener { confirmarEliminacion() }

        // Paso 2
        opciones.forEach { (conexion, vista) ->
            vista.setOnClickListener {
                conexionElegida = conexion
                actualizarOpciones()
            }
        }
        findViewById<View>(R.id.btnAtras).setOnClickListener { mostrarPaso(1) }
        btnVincular.setOnClickListener { confirmarHoja() }
    }

    // ---------- Hoja: abrir / cerrar / pasos ----------

    /** Sin argumento vincula uno nuevo; con un dispositivo lo edita. */
    private fun abrirHoja(existente: Dispositivo? = null) {
        if (hojaAbierta) return
        hojaAbierta = true
        atrasHoja.isEnabled = true
        editando = existente

        if (existente == null) {
            etNombre.text.clear()
            etUbicacion.text.clear()
            spTipo.setSelection(0)
            conexionElegida = null
        } else {
            etNombre.setText(existente.nombre)
            etUbicacion.setText(existente.ubicacion)
            spTipo.setSelection(existente.tipo.ordinal)
            conexionElegida = existente.conexion
        }

        // En edición el tipo queda bloqueado
        val bloqueado = existente != null
        spTipo.isEnabled = !bloqueado
        spTipo.alpha = if (bloqueado) 0.55f else 1f
        tvTipoBloqueado.isVisible = bloqueado
        btnVincular.setText(if (bloqueado) R.string.accion_guardar else R.string.accion_vincular)
        mostrarPaso(1)

        scrim.alpha = 0f
        scrim.isVisible = true
        scrim.animate().alpha(1f).setDuration(220).withEndAction(null).start()

        hoja.alpha = 0f
        hoja.isVisible = true
        hoja.doOnLayout {
            hoja.translationY = hoja.height.toFloat()
            hoja.alpha = 1f
            hoja.animate().translationY(0f).setDuration(280).withEndAction(null).start()
        }
    }

    private fun cerrarHoja() {
        if (!hojaAbierta) return
        hojaAbierta = false
        atrasHoja.isEnabled = false
        ocultarTeclado()

        scrim.animate().alpha(0f).setDuration(200)
            .withEndAction { scrim.isVisible = false }.start()
        hoja.animate().translationY(hoja.height.toFloat()).setDuration(250)
            .withEndAction { hoja.isVisible = false }.start()
    }

    private fun mostrarPaso(n: Int) {
        paso = n
        pasoDatos.isVisible = n == 1
        pasoConexion.isVisible = n == 2
        btnEliminar.isVisible = n == 1 && editando != null
        tvPaso.text = getString(R.string.hoja_paso_formato, n, 2)
        tvTituloHoja.setText(
            when {
                n == 2 -> R.string.hoja_titulo_conexion
                editando != null -> R.string.hoja_titulo_editar
                else -> R.string.hoja_titulo_datos
            }
        )
        segmento2.backgroundTintList = ColorStateList.valueOf(
            color(if (n == 2) R.color.verde_primario else R.color.borde_campo)
        )
        if (n == 1) {
            validarDatos()
        } else {
            tvSubtituloConexion.text =
                getString(R.string.conexion_subtitulo, etNombre.text.toString().trim())
            actualizarOpciones()
        }
    }

    private fun validarDatos() {
        btnContinuar.isEnabled =
            etNombre.text.isNotBlank() && etUbicacion.text.isNotBlank()
    }

    private fun irAlPaso2() {
        ocultarTeclado()
        mostrarPaso(2)
    }

    private fun actualizarOpciones() {
        opciones.forEach { (conexion, vista) -> vista.isSelected = conexion == conexionElegida }
        btnVincular.isEnabled = conexionElegida != null
    }

    /** Vincula uno nuevo o guarda los cambios del que se está editando. */
    private fun confirmarHoja() {
        val conexion = conexionElegida ?: return
        val nombre = etNombre.text.toString().trim()
        val ubicacion = etUbicacion.text.toString().trim()

        val existente = editando
        if (existente != null) {
            existente.nombre = nombre
            existente.ubicacion = ubicacion
            existente.conexion = conexion
            renderLista()
            cerrarHoja()
        } else {
            dispositivos.add(
                Dispositivo(Tipo.values()[spTipo.selectedItemPosition], nombre, ubicacion, conexion)
            )
            renderLista()
            cerrarHoja()
            scroll.postDelayed({ scroll.fullScroll(View.FOCUS_DOWN) }, 300)
        }
    }

    private fun confirmarEliminacion() {
        val d = editando ?: return
        ocultarTeclado()
        AlertDialog.Builder(this)
            .setTitle(R.string.eliminar_titulo)
            .setMessage(getString(R.string.eliminar_mensaje, d.nombre))
            .setNegativeButton(R.string.accion_cancelar, null)
            .setPositiveButton(R.string.accion_eliminar_confirmar) { _, _ ->
                dispositivos.remove(d)
                renderLista()
                cerrarHoja()
                Toast.makeText(this, R.string.toast_eliminado, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun ocultarTeclado() {
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }

    // ---------- Lista ----------

    private fun renderLista() {
        val total = dispositivos.size
        tvContador.text = resources.getQuantityString(R.plurals.disp_vinculados, total, total)
        tvChipRed.text = getString(R.string.red_contador, total, total)

        lista.removeAllViews()
        lista.isVisible = total > 0
        dispositivos.forEachIndexed { indice, d ->
            if (indice > 0) lista.addView(crearDivisor())
            lista.addView(crearFila(d))
        }
    }

    private fun crearDivisor(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp())
        setBackgroundColor(color(R.color.divisor))
    }

    private fun crearFila(d: Dispositivo): View {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            isClickable = true
            isFocusable = true
            val tv = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            setBackgroundResource(tv.resourceId)
            setOnClickListener { abrirHoja(d) }
        }

        // Icono
        val caja = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(38.dp(), 38.dp())
            background = ContextCompat.getDrawable(context, R.drawable.bg_caja_icono)?.mutate()
            backgroundTintList = ColorStateList.valueOf(color(R.color.verde_icono_fondo))
        }
        caja.addView(ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(21.dp(), 21.dp(), Gravity.CENTER)
            setImageResource(d.tipo.icono)
            imageTintList = ColorStateList.valueOf(color(R.color.verde_icono))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        fila.addView(caja)

        // Textos
        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 12.dp() }
        }
        columna.addView(texto(d.nombre, 14f, R.color.texto_principal, bold = true))
        columna.addView(
            texto(
                getString(R.string.fila_detalle, getString(d.tipo.rol), d.ubicacion),
                11f, R.color.texto_terciario, topMargin = 2.dp()
            )
        )

        val estado = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp() }
        }
        estado.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(6.dp(), 6.dp())
            background = ContextCompat.getDrawable(context, R.drawable.bg_punto_estado)?.mutate()
            backgroundTintList = ColorStateList.valueOf(color(R.color.verde_estado))
        })
        estado.addView(
            texto(
                getString(R.string.fila_conectado_por, getString(d.conexion.nombre)),
                10f, R.color.verde_lectura, bold = true, startMargin = 6.dp()
            )
        )
        columna.addView(estado)
        fila.addView(columna)

        // Chevron
        fila.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(18.dp(), 18.dp())
            setImageResource(R.drawable.ic_chevron_derecha)
            imageTintList = ColorStateList.valueOf(color(R.color.texto_hint))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        return fila
    }

    // ---------- Utilidades ----------

    private fun texto(
        contenido: String,
        sp: Float,
        @ColorRes color: Int,
        bold: Boolean = false,
        topMargin: Int = 0,
        startMargin: Int = 0,
    ): TextView = TextView(this).apply {
        text = contenido
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color(color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = topMargin
            marginStart = startMargin
        }
    }

    private fun color(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun avisoProximamente() {
        Toast.makeText(this, R.string.proximamente, Toast.LENGTH_SHORT).show()
    }
}