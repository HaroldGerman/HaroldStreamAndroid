package com.german.haroldstream

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.os.Handler

class MainActivity : AppCompatActivity(), PlayerManager.PlayerStateListener {

    private lateinit var adapter: CancionAdapter
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var tvSeccionTitulo: TextView

    // Adapters para Dashboard de Inicio
    private lateinit var adapterDashboardRec: DashboardHorizontalAdapter
    private lateinit var adapterDashboardPop: DashboardRankedAdapter
    private lateinit var adapterDashboardNuevas: DashboardHorizontalAdapter
    private lateinit var tvOfflineBanner: TextView
    private lateinit var progressBarMain: ProgressBar

    private lateinit var btnTabNube: Button
    private lateinit var btnTabFavoritas: Button
    private lateinit var btnTabDescargadas: Button
    private lateinit var tvResultadosCounter: TextView

    private var filterOnlyFavs = false
    private var filterMaxDuration = 600
    private var filterCategory = "Todas"

    // Vistas del Mini Reproductor Flotante
    private lateinit var layoutMiniPlayer: View
    private lateinit var ivMiniThumb: ImageView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPlayPause: ImageButton

    // Vistas del Sistema de Autenticación y Registro con PIN de 4 dígitos
    private lateinit var layoutAuthOverlay: View
    private lateinit var layoutRegistroForm: View
    private lateinit var layoutCodigoForm: View
    private lateinit var layoutPendienteForm: View
    private lateinit var etRegNombre: EditText
    private lateinit var etRegTelefono: EditText
    private lateinit var etRegPin: EditText
    private lateinit var tvCodigoInstruccion: TextView
    private lateinit var btnRegEnviar: Button
    private lateinit var btnVerificarPin: Button
    private lateinit var btnVerificarEstado: Button

    private var mediaController: MediaController? = null
    private var tabActual = TAB_NUBE
    private var listaCancionesActuales: List<Cancion> = emptyList()

    private lateinit var rvSugerencias: RecyclerView
    private lateinit var cvSugerenciasContainer: View
    private lateinit var suggestionAdapter: SuggestionAdapter

    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var isSelectingSuggestion = false

    companion object {
        const val TAB_NUBE = 0
        const val TAB_FAVORITAS = 1
        const val TAB_DESCARGADAS = 2
        const val TAB_ALBUMES = 3
        const val TAB_PLAYLISTS = 4
        const val DEFAULT_URL = "https://haroldstream.me/"
    }

    private val PREFS_NAME = "HaroldSoundPrefs"
    private val KEY_HISTORY_JSON = "history_songs_json"

    class SuggestionAdapter(
        private var sugerencias: List<String>,
        private var esHistorial: Boolean,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvText: TextView = view.findViewById(R.id.tv_suggestion_text)
            val ivIcon: ImageView = view.findViewById(R.id.iv_suggestion_icon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_suggestion, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val texto = sugerencias[position]
            holder.tvText.text = texto
            // Si es historial pone el reloj, si es sugerencia pone la lupa
            holder.ivIcon.setImageResource(if (esHistorial) android.R.drawable.ic_menu_recent_history else android.R.drawable.ic_menu_search)
            holder.itemView.setOnClickListener { onClick(texto) }
        }

        override fun getItemCount() = sugerencias.size

        fun actualizarData(nuevaLista: List<String>, historial: Boolean) {
            sugerencias = nuevaLista
            esHistorial = historial
            notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Cargar preferencia de tema antes de super.onCreate
        val themePrefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val isDarkMode = themePrefs.getBoolean("isDarkMode", true) // por defecto oscuro como la PWA
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PlayerManager.defaultServerUrl = DEFAULT_URL
        verificarPermisosLecturaAudio()

        // 1. Vincular vistas principales
        val etBusqueda = findViewById<EditText>(R.id.et_busqueda)
        val btnBuscar = findViewById<Button>(R.id.btn_buscar)
        progressBarMain = findViewById(R.id.progressBar)
        val rvResultados = findViewById<RecyclerView>(R.id.rv_resultados)
        tvSeccionTitulo = findViewById(R.id.tv_seccion_titulo)
        tvOfflineBanner = findViewById(R.id.tv_offline_banner)

        btnTabNube = findViewById(R.id.btn_tab_nube)
        btnTabFavoritas = findViewById(R.id.btn_tab_favoritas)
        btnTabDescargadas = findViewById(R.id.btn_tab_descargadas)
        tvResultadosCounter = findViewById(R.id.tv_resultados_counter)

        // Mini Reproductor
        layoutMiniPlayer = findViewById(R.id.layout_mini_player)
        ivMiniThumb = findViewById(R.id.iv_mini_thumb)
        tvMiniTitle = findViewById(R.id.tv_mini_title)
        tvMiniArtist = findViewById(R.id.tv_mini_artist)
        btnMiniPlayPause = findViewById(R.id.btn_mini_play_pause)

        // Vistas Auth Overlay
        layoutAuthOverlay = findViewById(R.id.layout_auth_overlay)
        layoutRegistroForm = findViewById(R.id.layout_registro_form)
        layoutCodigoForm = findViewById(R.id.layout_codigo_form)
        layoutPendienteForm = findViewById(R.id.layout_pendiente_form)
        etRegNombre = findViewById(R.id.et_reg_nombre)
        etRegTelefono = findViewById(R.id.et_reg_telefono)
        etRegPin = findViewById(R.id.et_reg_pin)
        tvCodigoInstruccion = findViewById(R.id.tv_codigo_instruccion)
        btnRegEnviar = findViewById(R.id.btn_reg_enviar)
        btnVerificarPin = findViewById(R.id.btn_verificar_pin)
        btnVerificarEstado = findViewById(R.id.btn_verificar_estado)

        // 2. Comprobar autorización de usuario
        comprobarEstadoAutorizacion()

        // 3. Conexión con PlaybackService
        conectarConServicio()

        PlayerManager.onAutoPlayNextListener = { cancion ->
            runOnUiThread {
                reproducirCancionSeleccionada(cancion)
            }
        }

        PlayerManager.onAutoPlayRelatedListener = { cancionAnterior ->
            val query = cancionAnterior?.canal ?: cancionAnterior?.titulo ?: ""
            if (query.isNotEmpty()) {
                val api = obtenerApiService()
                if (api != null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Buscando relacionados de: $query...", Toast.LENGTH_SHORT).show()
                    }
                    lifecycleScope.launch {
                        try {
                            val respuesta = api.buscarCancion(query)
                            if (respuesta.canciones.isNotEmpty()) {
                                val sugerencia = respuesta.canciones.firstOrNull { it.titulo != cancionAnterior?.titulo } ?: respuesta.canciones.first()
                                // Al reproducir esta canción, establecemos una nueva lista para que Siguiente siga funcionando
                                runOnUiThread {
                                    PlayerManager.establecerListaReproduccion(respuesta.canciones, respuesta.canciones.indexOf(sugerencia))
                                    reproducirCancionSeleccionada(sugerencia)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        // 4. Configurar el Adapter
        adapter = CancionAdapter(emptyList(), onClick = { cancionSeleccionada ->
            val posicion = listaCancionesActuales.indexOf(cancionSeleccionada)
            PlayerManager.establecerListaReproduccion(listaCancionesActuales, if (posicion != -1) posicion else 0)
            reproducirCancionSeleccionada(cancionSeleccionada)
        }, onFavoriteToggle = { cancion, nuevoEstado ->
            if (nuevoEstado) {
                marcarFavoritaYDescargar(cancion)
            } else {
                LocalMusicManager.quitarFavorito(this, cancion)
                Toast.makeText(this, "Removida de Favoritas", Toast.LENGTH_SHORT).show()
                if (tabActual == TAB_FAVORITAS) cargarPestañaFavoritas()
            }
        })

        playlistAdapter = PlaylistAdapter(emptyList(), onClick = { playlistSelected ->
            cargarCancionesDePlaylist(playlistSelected)
        })

        rvResultados.layoutManager = LinearLayoutManager(this)
        rvResultados.adapter = adapter

        // 5. Listeners de Pestañas
        btnTabNube.setOnClickListener { cambiarPestaña(TAB_NUBE, etBusqueda) }
        btnTabFavoritas.setOnClickListener { cambiarPestaña(TAB_FAVORITAS, etBusqueda) }
        btnTabDescargadas.setOnClickListener { cambiarPestaña(TAB_DESCARGADAS, etBusqueda) }

        val btnTabPlaylists = findViewById<Button>(R.id.btn_tab_playlists)
        btnTabPlaylists?.setOnClickListener { cambiarPestaña(TAB_PLAYLISTS, etBusqueda) }

        val btnTabAlbumes = findViewById<Button>(R.id.btn_tab_albumes)
        btnTabAlbumes?.setOnClickListener { cambiarPestaña(TAB_ALBUMES, etBusqueda) }

        // 5b. Limpiador y Configuración de Filtros
        val btnClearSearch = findViewById<ImageButton>(R.id.btn_clear_search)
        val btnHeaderConfig = findViewById<ImageButton>(R.id.btn_header_config)

        btnClearSearch?.setOnClickListener {
            etBusqueda.setText("")
            btnClearSearch.visibility = View.GONE
            if (tabActual == TAB_NUBE) {
                cargarHistorialYRecomendaciones()
            }
        }

        btnHeaderConfig?.setOnClickListener {
            mostrarDialogoFiltros()
        }

        val btnThemeToggle = findViewById<ImageButton>(R.id.btn_theme_toggle)
        btnThemeToggle?.setImageResource(if (isDarkMode) R.drawable.ic_theme_light else R.drawable.ic_theme_dark)
        btnThemeToggle?.setOnClickListener {
            val themePrefsEdit = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            val currentDarkMode = themePrefsEdit.getBoolean("isDarkMode", true)
            val newDarkMode = !currentDarkMode
            
            themePrefsEdit.edit().putBoolean("isDarkMode", newDarkMode).apply()
            
            if (newDarkMode) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        // 5c. Barra de Navegación Inferior Fija
        val navInicio = findViewById<View>(R.id.nav_inicio)
        val navBuscar = findViewById<View>(R.id.nav_buscar)
        val navPlaylists = findViewById<View>(R.id.nav_playlists)
        val navFavoritas = findViewById<View>(R.id.nav_favoritas)
        val navDescargas = findViewById<View>(R.id.nav_descargas)

        navInicio?.setOnClickListener {
            etBusqueda.setText("")
            actualizarBottomNavSelection(R.id.nav_inicio)
            tabActual = TAB_NUBE
            actualizarEstadoPestañas(etBusqueda)
            ocultarSugerenciasYTeclado()
            
            findViewById<View>(R.id.layout_app_main_content)?.visibility = View.GONE
            findViewById<View>(R.id.scroll_inicio_dashboard)?.visibility = View.VISIBLE
            cargarDatosDashboard()
        }
        navBuscar?.setOnClickListener {
            actualizarBottomNavSelection(R.id.nav_buscar)
            tabActual = TAB_NUBE
            actualizarEstadoPestañas(etBusqueda)
            
            findViewById<View>(R.id.layout_app_main_content)?.visibility = View.VISIBLE
            findViewById<View>(R.id.scroll_inicio_dashboard)?.visibility = View.GONE

            etBusqueda.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etBusqueda, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            val textoActual = etBusqueda.text.toString().trim()
            if (textoActual.isEmpty()) {
                mostrarHistorialBusquedaGlobal()
            }
        }
        navPlaylists?.setOnClickListener {
            cambiarPestaña(TAB_PLAYLISTS, etBusqueda)
        }
        navFavoritas?.setOnClickListener {
            cambiarPestaña(TAB_FAVORITAS, etBusqueda)
        }
        navDescargas?.setOnClickListener {
            cambiarPestaña(TAB_DESCARGADAS, etBusqueda)
        }

        // Selección inicial del Bottom Nav - Inicio
        actualizarBottomNavSelection(R.id.nav_inicio)

        // Mini reproductor listener
        layoutMiniPlayer.setOnClickListener {
            PlayerManager.currentCancion?.let { cancion ->
                PlayerManager.currentStreamUrl?.let { streamUrl ->
                    abrirPlayerActivity(cancion, streamUrl)
                }
            }
        }

        btnMiniPlayPause.setOnClickListener {
            PlayerManager.togglePlayPause()
        }

        btnBuscar.setOnClickListener {
            if (tabActual != TAB_NUBE) cambiarPestaña(TAB_NUBE, etBusqueda)
            actualizarBottomNavSelection(R.id.nav_buscar)
            ejecutarBusqueda(etBusqueda, progressBarMain)
        }

        etBusqueda.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (tabActual != TAB_NUBE) cambiarPestaña(TAB_NUBE, etBusqueda)
                actualizarBottomNavSelection(R.id.nav_buscar)
                ejecutarBusqueda(etBusqueda, progressBarMain)
                true
            } else {
                false
            }
        }

        etBusqueda.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // --- BLOQUEO CLAVE ---
                if (isSelectingSuggestion) return
                // ---------------------

                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    btnClearSearch?.visibility = View.VISIBLE
                } else {
                    btnClearSearch?.visibility = View.GONE
                }

                if (query.isEmpty() && tabActual == TAB_NUBE) {
                    mostrarHistorialBusquedaGlobal()
                    cargarHistorialYRecomendaciones()
                } else if (tabActual == TAB_NUBE) {
                    debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
                    debounceRunnable = Runnable {
                        buscarSugerenciasApi(query)
                    }
                    debounceHandler.postDelayed(debounceRunnable!!, 250)
                }
            }
        })

        rvSugerencias = findViewById(R.id.rv_sugerencias)
        cvSugerenciasContainer = findViewById(R.id.cv_sugerencias_container)

        suggestionAdapter = SuggestionAdapter(emptyList(), true) { terminoSelect ->
            isSelectingSuggestion = true // Activamos el escudo para el TextWatcher
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }

            cvSugerenciasContainer.visibility = View.GONE

            etBusqueda.setText(terminoSelect)
            etBusqueda.setSelection(terminoSelect.length)

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(etBusqueda.windowToken, 0)

            ejecutarBusqueda(etBusqueda, progressBarMain)

            // Soltamos el escudo después de un momento
            debounceHandler.postDelayed({
                isSelectingSuggestion = false
            }, 600)
        }
        rvSugerencias.layoutManager = LinearLayoutManager(this)
        rvSugerencias.adapter = suggestionAdapter

        // Mostrar historial al hacer click en el buscador
        etBusqueda.setOnFocusChangeListener { _, hasFocus ->
            if (isSelectingSuggestion) return@setOnFocusChangeListener // <--- Si estamos eligiendo, no hacemos nada

            if (hasFocus && etBusqueda.text.toString().trim().isEmpty()) {
                mostrarHistorialBusquedaGlobal()
            } else {
                cvSugerenciasContainer.visibility = View.GONE
            }
        }

        // --- REGISTRO DIRECTO: SIN PIN NI VERIFICACIÓN ---
        btnRegEnviar.setOnClickListener {
            val nombre = etRegNombre.text.toString().trim()
            val telefono = etRegTelefono.text.toString().trim()
            if (nombre.isEmpty() || telefono.isEmpty()) {
                Toast.makeText(this, "Por favor completa tu Nombre y Teléfono", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBarMain.visibility = View.VISIBLE
            lifecycleScope.launch {
                val result = UserAuthManager.registrarUsuario(this@MainActivity, DEFAULT_URL, nombre, telefono)
                progressBarMain.visibility = View.GONE
                if (result.isSuccess) {
                    val resMap = result.getOrNull()
                    val status = resMap?.get("status") as? String
                    val approved = (resMap?.get("approved") as? Boolean) == true || resMap?.get("user_status") == "approved"
                    if (status == "success" && approved) {
                        Toast.makeText(this@MainActivity, "✅ Registro completado", Toast.LENGTH_SHORT).show()
                        desbloquearApp()
                    } else {
                        val msg = resMap?.get("message") as? String ?: "Error al procesar el registro"
                        Toast.makeText(this@MainActivity, "❌ $msg", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    Toast.makeText(this@MainActivity, "Error de conexión: ${ex?.message ?: "No se pudo conectar al servidor"}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // PASO 2: Verificar Código PIN de 4 dígitos
        btnVerificarPin.setOnClickListener {
            val pin = etRegPin.text.toString().trim()
            if (pin.length != 4) {
                Toast.makeText(this, "Por favor ingresa tu código PIN de 4 dígitos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBarMain.visibility = View.VISIBLE
            lifecycleScope.launch {
                val result = UserAuthManager.verificarCodigoPin(this@MainActivity, DEFAULT_URL, pin)
                progressBarMain.visibility = View.GONE
                if (result.isSuccess) {
                    val resMap = result.getOrNull()
                    val status = resMap?.get("status") as? String
                    if (status == "success" || resMap?.get("user_status") == "pending") {
                        Toast.makeText(this@MainActivity, "✅ ¡Número verificado! Esperando aprobación del admin.", Toast.LENGTH_SHORT).show()
                        mostrarPantallaPendiente()
                    } else {
                        val msg = resMap?.get("message") as? String ?: "Código PIN incorrecto"
                        Toast.makeText(this@MainActivity, "❌ $msg", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    Toast.makeText(this@MainActivity, "Error de conexión: ${ex?.message ?: "Servidor no disponible"}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // PASO 3: Verificar Aprobación del Admin (/admin)
        btnVerificarEstado.setOnClickListener {
            progressBarMain.visibility = View.VISIBLE
            lifecycleScope.launch {
                val result = UserAuthManager.verificarEstadoEnServidor(this@MainActivity, DEFAULT_URL)
                progressBarMain.visibility = View.GONE
                if (result.isSuccess) {
                    val status = result.getOrNull()
                    if (status == "approved") {
                        Toast.makeText(this@MainActivity, "¡Acceso Aprobado por Harold! 🎉", Toast.LENGTH_SHORT).show()
                        desbloquearApp()
                    } else if (status == "blocked") {
                        Toast.makeText(this@MainActivity, "🚫 Acceso Bloqueado por el Administrador", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Aún en espera de aprobación en el panel /admin ⏳", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    Toast.makeText(this@MainActivity, "Error de conexión con AWS: ${ex?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        inicializarDashboard()
        navInicio?.performClick()
        comprobarActualizaciones()

        // --- DETECTAR ENLACE COMPARTIDO (DEEP LINKING) ---
        val uri: Uri? = intent.data
        if (uri != null && uri.getQueryParameter("play_url") != null) {
            val playUrl = uri.getQueryParameter("play_url") ?: ""
            val title = uri.getQueryParameter("title") ?: "Canción Compartida"
            val canal = uri.getQueryParameter("canal") ?: "HaroldStream"
            val thumb = uri.getQueryParameter("thumb") ?: ""

            val cancionCompartida = Cancion(
                titulo = title,
                url = playUrl,
                canal = canal,
                thumbnail = thumb
            )

            Handler(Looper.getMainLooper()).postDelayed({
                reproducirCancionSeleccionada(cancionCompartida)
            }, 1000)
        }
    }

    private fun comprobarEstadoAutorizacion() {
        val aprobadoLocalmente = UserAuthManager.esAprobadoLocalmente(this)
        
        // Desbloquear inmediatamente si ya estaba aprobado localmente, 
        // pero NO hacer return, seguir comprobando con el servidor en segundo plano.
        if (aprobadoLocalmente) {
            desbloquearApp()
        }

        lifecycleScope.launch {
            val res = UserAuthManager.verificarEstadoEnServidor(this@MainActivity, DEFAULT_URL)
            if (res.isSuccess) {
                val status = res.getOrNull()
                when (status) {
                    "approved" -> desbloquearApp()
                    "code_sent", "pending" -> mostrarPantallaRegistro()
                    "blocked" -> {
                        mostrarPantallaPendiente()
                        Toast.makeText(this@MainActivity, "🚫 Acceso Bloqueado por el Administrador", Toast.LENGTH_LONG).show()
                    }
                    "unregistered" -> {
                        mostrarPantallaRegistro()
                        if (aprobadoLocalmente) {
                            Toast.makeText(this@MainActivity, "🚫 Acceso revocado (Usuario eliminado)", Toast.LENGTH_LONG).show()
                        }
                    }
                    else -> mostrarPantallaRegistro()
                }
            } else {
                // Si falla el servidor pero estaba aprobado localmente, lo dejamos pasar por ahora (modo offline).
                if (!aprobadoLocalmente) {
                    mostrarPantallaRegistro()
                }
            }
        }
    }

    private fun comprobarActualizaciones() {
        val api = obtenerApiService() ?: return
        lifecycleScope.launch {
            try {
                val response = api.obtenerVersion()
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }

                if (response.versionCode > currentVersionCode) {
                    mostrarDialogoActualizacion(response.versionName, response.releaseNotes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun mostrarDialogoActualizacion(nuevaVersionName: String, notasNuevaVersion: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("⚡ Nueva actualización disponible (v$nuevaVersionName)")
        builder.setMessage("Notas de la versión:\n$notasNuevaVersion\n\n¿Deseas descargar la actualización ahora?")
        
        builder.setPositiveButton("Actualizar") { _, _ ->
            var baseUrl = DEFAULT_URL.trim()
            if (!baseUrl.endsWith("/")) baseUrl += "/"
            val downloadUrl = "${baseUrl}api/download-apk"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
            startActivity(intent)
        }
        
        builder.setNegativeButton("Más tarde") { dialog, _ ->
            dialog.dismiss()
        }
        
        val dialog = builder.create()
        dialog.show()
    }

    private fun mostrarPantallaRegistro() {
        layoutAuthOverlay.visibility = View.VISIBLE
        layoutRegistroForm.visibility = View.VISIBLE
        layoutCodigoForm.visibility = View.GONE
        layoutPendienteForm.visibility = View.GONE
    }

    private fun mostrarPantallaCodigoPin() {
        layoutAuthOverlay.visibility = View.VISIBLE
        layoutRegistroForm.visibility = View.GONE
        layoutCodigoForm.visibility = View.VISIBLE
        layoutPendienteForm.visibility = View.GONE
        tvCodigoInstruccion.text = "Ingresa el código PIN de 4 dígitos enviado a tu número de WhatsApp / Celular:"
    }

    private fun mostrarPantallaPendiente() {
        layoutAuthOverlay.visibility = View.VISIBLE
        layoutRegistroForm.visibility = View.GONE
        layoutCodigoForm.visibility = View.GONE
        layoutPendienteForm.visibility = View.VISIBLE
    }

    private fun desbloquearApp() {
        layoutAuthOverlay.visibility = View.GONE
        lifecycleScope.launch {
            UpdateManager.checkForUpdates(this@MainActivity, DEFAULT_URL)
        }
    }

    private fun reproducirCancionSeleccionada(cancion: Cancion) {
        val url = cancion.url
        if (!url.isNullOrEmpty()) {
            guardarEnHistorial(cancion)
            if (cancion.isDownloaded || url.startsWith("content://") || url.startsWith("file://") || url.contains("/descargas/")) {
                PlayerManager.playCancion(this, cancion, url)
            } else {
                reproducirStreamingDirecto(cancion, progressBarMain)
            }
        } else {
            Toast.makeText(this, "URL no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun marcarFavoritaYDescargar(cancion: Cancion) {
        val api = obtenerApiService()
        val urlOriginal = cancion.url

        if (api != null && !urlOriginal.isNullOrEmpty() && urlOriginal.contains("youtube")) {
            progressBarMain.visibility = View.VISIBLE
            Toast.makeText(this, "⭐ Guardando en Favoritas y Descargando MP3...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                try {
                    val respuesta = api.descargarCancion(youtubeUrl = urlOriginal)
                    if (respuesta.status == "success" && respuesta.url != null) {
                        val cancionCompleta = cancion.copy(
                            url = respuesta.url,
                            titulo = respuesta.titulo ?: cancion.titulo,
                            thumbnail = respuesta.thumbnail ?: cancion.thumbnail,
                            canal = respuesta.canal ?: cancion.canal,
                            duracion = respuesta.duracion ?: cancion.duracion,
                            isFavorite = true
                        )
                        LocalMusicManager.guardarFavorito(this@MainActivity, cancionCompleta)
                        LocalMusicManager.descargarMP3EnCelular(this@MainActivity, respuesta.url, cancionCompleta.titulo ?: "Canción")
                        Toast.makeText(this@MainActivity, "⭐ Canción guardada en Favoritas y descargada 📥", Toast.LENGTH_SHORT).show()
                    } else {
                        LocalMusicManager.guardarFavorito(this@MainActivity, cancion)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    LocalMusicManager.guardarFavorito(this@MainActivity, cancion)
                } finally {
                    progressBarMain.visibility = View.GONE
                    if (tabActual == TAB_FAVORITAS) cargarPestañaFavoritas()
                }
            }
        } else {
            LocalMusicManager.guardarFavorito(this, cancion)
            if (!urlOriginal.isNullOrEmpty()) {
                LocalMusicManager.descargarMP3EnCelular(this, urlOriginal, cancion.titulo ?: "Canción")
            }
            Toast.makeText(this, "⭐ Guardada en Favoritas", Toast.LENGTH_SHORT).show()
            if (tabActual == TAB_FAVORITAS) cargarPestañaFavoritas()
        }
    }

    private fun verificarPermisosLecturaAudio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 200)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 201)
            }
        }
    }

    private fun ocultarSugerenciasYTeclado() {
        val etBusqueda = findViewById<EditText>(R.id.et_busqueda)
        val cvSugerenciasContainer = findViewById<View>(R.id.cv_sugerencias_container)
        cvSugerenciasContainer?.visibility = View.GONE
        etBusqueda?.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(etBusqueda?.windowToken, 0)
    }

    private fun cambiarPestaña(nuevaPestaña: Int, etBusqueda: EditText) {
        tabActual = nuevaPestaña
        actualizarEstadoPestañas(etBusqueda)
        ocultarSugerenciasYTeclado()

        tvResultadosCounter.visibility = View.GONE

        val layoutMainContent = findViewById<View>(R.id.layout_app_main_content)
        val scrollDashboard = findViewById<View>(R.id.scroll_inicio_dashboard)
        val rvResultados = findViewById<RecyclerView>(R.id.rv_resultados)

        when (tabActual) {
            TAB_NUBE -> {
                actualizarBottomNavSelection(R.id.nav_buscar)
                tvOfflineBanner.visibility = View.GONE
                layoutMainContent?.visibility = View.VISIBLE
                scrollDashboard?.visibility = View.GONE
                rvResultados?.adapter = adapter
                val query = etBusqueda.text.toString().trim()
                if (query.isNotEmpty()) {
                    val api = obtenerApiService()
                    if (api != null) {
                        buscarEnYouTube(query, api, progressBarMain)
                    }
                } else {
                    cargarHistorialYRecomendaciones()
                }
            }
            TAB_PLAYLISTS -> {
                actualizarBottomNavSelection(R.id.nav_playlists)
                layoutMainContent?.visibility = View.VISIBLE
                scrollDashboard?.visibility = View.GONE
                cargarPestañaPlaylists()
            }
            TAB_FAVORITAS -> {
                actualizarBottomNavSelection(R.id.nav_favoritas)
                layoutMainContent?.visibility = View.VISIBLE
                scrollDashboard?.visibility = View.GONE
                rvResultados?.adapter = adapter
                cargarPestañaFavoritas()
            }
            TAB_DESCARGADAS -> {
                actualizarBottomNavSelection(R.id.nav_descargas)
                layoutMainContent?.visibility = View.VISIBLE
                scrollDashboard?.visibility = View.GONE
                rvResultados?.adapter = adapter
                cargarPestañaDescargadas()
            }
            TAB_ALBUMES -> {
                actualizarBottomNavSelection(R.id.nav_buscar)
                tvOfflineBanner.visibility = View.GONE
                layoutMainContent?.visibility = View.VISIBLE
                scrollDashboard?.visibility = View.GONE
                val queryActual = etBusqueda.text.toString().trim()
                cargarAlbumes(queryActual)
            }
        }
    }

    private fun actualizarEstadoPestañas(etBusqueda: EditText) {
        val btnTabPlaylists = findViewById<Button>(R.id.btn_tab_playlists)
        val btnTabAlbumes = findViewById<Button>(R.id.btn_tab_albumes)
        btnTabNube.setBackgroundResource(if (tabActual == TAB_NUBE) R.drawable.bg_pill_active else R.drawable.bg_pill_inactive)
        btnTabPlaylists?.setBackgroundResource(if (tabActual == TAB_PLAYLISTS) R.drawable.bg_pill_active else R.drawable.bg_pill_inactive)
        btnTabFavoritas.setBackgroundResource(if (tabActual == TAB_FAVORITAS) R.drawable.bg_pill_active else R.drawable.bg_pill_inactive)
        btnTabDescargadas.setBackgroundResource(if (tabActual == TAB_DESCARGADAS) R.drawable.bg_pill_active else R.drawable.bg_pill_inactive)
        btnTabAlbumes?.setBackgroundResource(if (tabActual == TAB_ALBUMES) R.drawable.bg_pill_active else R.drawable.bg_pill_inactive)

        btnTabNube.setTextColor(if (tabActual == TAB_NUBE) Color.parseColor("#FFFFFF") else Color.parseColor("#788295"))
        btnTabPlaylists?.setTextColor(if (tabActual == TAB_PLAYLISTS) Color.parseColor("#FFFFFF") else Color.parseColor("#788295"))
        btnTabFavoritas.setTextColor(if (tabActual == TAB_FAVORITAS) Color.parseColor("#FFFFFF") else Color.parseColor("#788295"))
        btnTabDescargadas.setTextColor(if (tabActual == TAB_DESCARGADAS) Color.parseColor("#FFFFFF") else Color.parseColor("#788295"))
        btnTabAlbumes?.setTextColor(if (tabActual == TAB_ALBUMES) Color.parseColor("#FFFFFF") else Color.parseColor("#788295"))
    }

    private fun actualizarBottomNavSelection(idSeleccionado: Int) {
        val violetColor = Color.parseColor("#7356F1")
        val secColor = Color.parseColor("#788295")

        val ivInicio = findViewById<ImageView>(R.id.iv_nav_inicio)
        val tvInicio = findViewById<TextView>(R.id.tv_nav_inicio)
        val ivBuscar = findViewById<ImageView>(R.id.iv_nav_buscar)
        val tvBuscar = findViewById<TextView>(R.id.tv_nav_buscar)
        val ivPlaylists = findViewById<ImageView>(R.id.iv_nav_playlists)
        val tvPlaylists = findViewById<TextView>(R.id.tv_nav_playlists)
        val ivFavoritas = findViewById<ImageView>(R.id.iv_nav_favoritas)
        val tvFavoritas = findViewById<TextView>(R.id.tv_nav_favoritas)
        val ivDescargas = findViewById<ImageView>(R.id.iv_nav_descargas)
        val tvDescargas = findViewById<TextView>(R.id.tv_nav_descargas)

        ivInicio?.setColorFilter(if (idSeleccionado == R.id.nav_inicio) violetColor else secColor)
        tvInicio?.setTextColor(if (idSeleccionado == R.id.nav_inicio) violetColor else secColor)

        ivBuscar?.setColorFilter(if (idSeleccionado == R.id.nav_buscar) violetColor else secColor)
        tvBuscar?.setTextColor(if (idSeleccionado == R.id.nav_buscar) violetColor else secColor)

        ivPlaylists?.setColorFilter(if (idSeleccionado == R.id.nav_playlists) violetColor else secColor)
        tvPlaylists?.setTextColor(if (idSeleccionado == R.id.nav_playlists) violetColor else secColor)

        ivFavoritas?.setColorFilter(if (idSeleccionado == R.id.nav_favoritas) violetColor else secColor)
        tvFavoritas?.setTextColor(if (idSeleccionado == R.id.nav_favoritas) violetColor else secColor)

        ivDescargas?.setColorFilter(if (idSeleccionado == R.id.nav_descargas) violetColor else secColor)
        tvDescargas?.setTextColor(if (idSeleccionado == R.id.nav_descargas) violetColor else secColor)
    }

    private fun mostrarDialogoFiltros() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_filters)
        dialog.window?.apply {
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setGravity(android.view.Gravity.BOTTOM)
            attributes?.windowAnimations = android.R.style.Animation_InputMethod // slide up
        }

        val cbFavs = dialog.findViewById<CheckBox>(R.id.cb_filter_favs)
        val sbDuration = dialog.findViewById<SeekBar>(R.id.sb_filter_duration)
        val tvDurationVal = dialog.findViewById<TextView>(R.id.tv_filter_duration_val)
        val pillTodas = dialog.findViewById<TextView>(R.id.pill_cat_todas)
        val pillLentas = dialog.findViewById<TextView>(R.id.pill_cat_lentas)
        val pillRapidas = dialog.findViewById<TextView>(R.id.pill_cat_rapidas)
        val btnClear = dialog.findViewById<Button>(R.id.btn_filter_clear)
        val btnApply = dialog.findViewById<Button>(R.id.btn_filter_apply)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btn_close_filters)

        // Restaurar estado
        cbFavs?.isChecked = filterOnlyFavs
        sbDuration?.progress = filterMaxDuration
        if (filterMaxDuration >= 600) {
            tvDurationVal?.text = "10:00+"
        } else {
            val m = filterMaxDuration / 60
            val s = filterMaxDuration % 60
            tvDurationVal?.text = String.format("%d:%02d", m, s)
        }

        fun updatePillSelection(selectedCat: String) {
            filterCategory = selectedCat
            val activeBg = R.drawable.bg_pill_active
            val inactiveBg = R.drawable.bg_pill_inactive
            val activeColor = Color.WHITE
            val secColor = Color.parseColor("#788295")

            pillTodas?.setBackgroundResource(if (selectedCat == "Todas") activeBg else inactiveBg)
            pillTodas?.setTextColor(if (selectedCat == "Todas") activeColor else secColor)

            pillLentas?.setBackgroundResource(if (selectedCat == "Lentas") activeBg else inactiveBg)
            pillLentas?.setTextColor(if (selectedCat == "Lentas") activeColor else secColor)

            pillRapidas?.setBackgroundResource(if (selectedCat == "Rápidas") activeBg else inactiveBg)
            pillRapidas?.setTextColor(if (selectedCat == "Rápidas") activeColor else secColor)
        }

        updatePillSelection(filterCategory)

        pillTodas?.setOnClickListener { updatePillSelection("Todas") }
        pillLentas?.setOnClickListener { updatePillSelection("Lentas") }
        pillRapidas?.setOnClickListener { updatePillSelection("Rápidas") }

        sbDuration?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress >= 600) {
                    tvDurationVal?.text = "10:00+"
                } else {
                    val m = progress / 60
                    val s = progress % 60
                    tvDurationVal?.text = String.format("%d:%02d", m, s)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnClose?.setOnClickListener { dialog.dismiss() }

        btnClear?.setOnClickListener {
            cbFavs?.isChecked = false
            sbDuration?.progress = 600
            tvDurationVal?.text = "10:00+"
            updatePillSelection("Todas")
        }

        btnApply?.setOnClickListener {
            filterOnlyFavs = cbFavs?.isChecked == true
            filterMaxDuration = sbDuration?.progress ?: 600
            
            aplicarFiltros()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun aplicarFiltros() {
        var filtradas = listaCancionesActuales.toMutableList()

        if (filterOnlyFavs) {
            filtradas = filtradas.filter { LocalMusicManager.esFavorito(this, it) }.toMutableList()
        }

        if (filterMaxDuration < 600) {
            filtradas = filtradas.filter { cancion ->
                val durStr = cancion.duracion ?: ""
                if (durStr.isNotEmpty() && durStr.contains(":")) {
                    val parts = durStr.split(":")
                    if (parts.size >= 2) {
                        val m = parts[0].toIntOrNull() ?: 0
                        val s = parts[1].toIntOrNull() ?: 0
                        val totalSec = m * 60 + s
                        totalSec <= filterMaxDuration
                    } else {
                        true
                    }
                } else {
                    true
                }
            }.toMutableList()
        }

        adapter.actualizarLista(filtradas)
        tvSeccionTitulo.text = "Resultados con filtros aplicados"
        tvResultadosCounter.text = "${filtradas.size} canciones"
        tvResultadosCounter.visibility = View.VISIBLE
    }

    private fun cargarPestañaPlaylists() {
        ocultarSugerenciasYTeclado()
        val rvResultados = findViewById<RecyclerView>(R.id.rv_resultados)
        rvResultados?.adapter = playlistAdapter

        val customPlaylists = LocalMusicManager.obtenerPlaylistsPersonalizadas(this)
        val customMapped = customPlaylists.map { cp ->
            Playlist(
                titulo = cp.nombre,
                url = "custom:${cp.id}",
                thumbnail = if (cp.canciones.isNotEmpty()) cp.canciones[0].thumbnail else null,
                video_count = cp.canciones.size,
                canal = "${cp.canciones.size} ${if (cp.canciones.size == 1) "canción" else "canciones"}"
            )
        }

        tvSeccionTitulo.text = "Playlists (${customPlaylists.size})"
        playlistAdapter.actualizarLista(customMapped)
        progressBarMain.visibility = View.GONE
    }

    private fun cargarPestañaFavoritas() {
        ocultarSugerenciasYTeclado()
        val favs = LocalMusicManager.obtenerFavoritos(this)
        listaCancionesActuales = favs
        tvSeccionTitulo.text = "Mis Canciones Favoritas (${favs.size})"
        adapter.actualizarLista(favs)
    }

    private fun cargarPestañaDescargadas() {
        ocultarSugerenciasYTeclado()
        val locales = LocalMusicManager.cargarCancionesLocalesMP3(this)
        listaCancionesActuales = locales
        tvSeccionTitulo.text = "Canciones Guardadas Offline (${locales.size})"
        adapter.actualizarLista(locales)
    }

    private fun activarModoOffline() {
        tvOfflineBanner.visibility = View.VISIBLE
        cambiarPestaña(TAB_FAVORITAS, findViewById(R.id.et_busqueda))
    }

    private fun conectarConServicio() {
        try {
            val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
            controllerFuture.addListener(
                {
                    try {
                        mediaController = controllerFuture.get()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                ContextCompat.getMainExecutor(this)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        PlayerManager.addListener(this)
        actualizarEstadoMiniPlayer()
        if (tabActual == TAB_FAVORITAS) cargarPestañaFavoritas()
        else if (tabActual == TAB_DESCARGADAS) cargarPestañaDescargadas()
    }

    override fun onPause() {
        super.onPause()
        PlayerManager.removeListener(this)
    }

    private fun actualizarEstadoMiniPlayer() {
        val cancion = PlayerManager.currentCancion
        val player = PlayerManager.player

        if (cancion != null && player != null) {
            layoutMiniPlayer.visibility = View.VISIBLE
            tvMiniTitle.text = cancion.titulo ?: "Canción"
            tvMiniArtist.text = cancion.canal ?: "HaroldSound"

            ivMiniThumb.load(cancion.thumbnail) {
                crossfade(true)
                placeholder(android.R.drawable.ic_media_play)
            }

            if (player.isPlaying) {
                btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        } else {
            layoutMiniPlayer.visibility = View.GONE
        }
    }

    override fun onSongChanged(cancion: Cancion?) {
        actualizarEstadoMiniPlayer()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            btnMiniPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun onPlaybackReady(durationMs: Long) {}

    // --- Funciones para Autocompletado ---
    private fun mostrarHistorialBusquedaGlobal() {
        val historial = LocalMusicManager.obtenerHistorialBusquedas(this)
        if (historial.isNotEmpty()) {
            suggestionAdapter.actualizarData(historial, true)
            cvSugerenciasContainer.visibility = View.VISIBLE
        } else {
            cvSugerenciasContainer.visibility = View.GONE
        }
    }

    private fun buscarSugerenciasApi(query: String) {
        val api = obtenerApiService() ?: return
        lifecycleScope.launch {
            try {
                val respuesta = api.obtenerSugerencias(query)
                if (!respuesta.suggestions.isNullOrEmpty()) {
                    suggestionAdapter.actualizarData(respuesta.suggestions, false)
                    cvSugerenciasContainer.visibility = View.VISIBLE
                } else {
                    cvSugerenciasContainer.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun ejecutarBusqueda(etBusqueda: EditText, progressBar: ProgressBar) {
        val termino = etBusqueda.text.toString().trim()

        // 1. Ocultar el dropdown y guardar en el historial local al buscar
        cvSugerenciasContainer.visibility = View.GONE
        if (termino.isNotEmpty()) {
            LocalMusicManager.guardarBusquedaHistorial(this, termino)
        }

        // Mostrar listado de búsqueda y ocultar dashboard de inicio
        findViewById<View>(R.id.layout_app_main_content)?.visibility = View.VISIBLE
        findViewById<View>(R.id.scroll_inicio_dashboard)?.visibility = View.GONE

        if (tabActual == TAB_ALBUMES) {
            cargarAlbumes(termino)
            return
        }

        val rvResultados = findViewById<RecyclerView>(R.id.rv_resultados)
        rvResultados?.adapter = adapter

        if (termino.isEmpty()) {
            cargarHistorialYRecomendaciones()
            return
        }

        val api = obtenerApiService()
        if (api == null) {
            activarModoOffline()
            return
        }

        buscarEnYouTube(termino, api, progressBar)
    }

    private fun obtenerApiService(): ApiService? {
        var rawUrl = DEFAULT_URL
        if (!rawUrl.endsWith("/")) {
            rawUrl += "/"
        }

        return try {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(rawUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            retrofit.create(ApiService::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buscarEnYouTube(termino: String, api: ApiService, progressBar: ProgressBar) {
        progressBar.visibility = View.VISIBLE
        tvSeccionTitulo.text = "Resultados encontrados"
        tvResultadosCounter.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val respuesta = api.buscarCancion(termino)
                if (respuesta.canciones.isNotEmpty()) {
                    tvOfflineBanner.visibility = View.GONE
                    listaCancionesActuales = respuesta.canciones
                    adapter.actualizarLista(respuesta.canciones)
                    tvResultadosCounter.text = "${respuesta.canciones.size} canciones"
                    tvResultadosCounter.visibility = View.VISIBLE
                } else {
                    Toast.makeText(this@MainActivity, "No se encontraron resultados", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activarModoOffline()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun reproducirStreamingDirecto(cancion: Cancion, progressBar: ProgressBar) {
        val urlOriginal = cancion.url
        if (!urlOriginal.isNullOrEmpty()) {
            val encodedUrl = Uri.encode(urlOriginal)
            val baseUrl = if (DEFAULT_URL.endsWith("/")) DEFAULT_URL else "$DEFAULT_URL/"
            val devId = UserAuthManager.obtenerDeviceId(this)
            val streamUrl = "${baseUrl}stream?url=$encodedUrl&deviceId=$devId"
            
            val cancionStream = cancion.copy(
                url = urlOriginal // Guardamos la original para poder descargarla después si la hace favorita
            )
            
            Toast.makeText(this, "Conectando al stream...", Toast.LENGTH_SHORT).show()
            guardarEnHistorial(cancionStream)
            PlayerManager.playCancion(this@MainActivity, cancionStream, streamUrl)
            abrirPlayerActivity(cancionStream, streamUrl)
        } else {
            Toast.makeText(this@MainActivity, "URL de canción inválida", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirPlayerActivity(cancion: Cancion, streamUrl: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
            putExtra(PlayerActivity.EXTRA_TITLE, cancion.titulo ?: "Canción")
            putExtra(PlayerActivity.EXTRA_THUMBNAIL, cancion.thumbnail)
            putExtra(PlayerActivity.EXTRA_CANAL, cancion.canal)
            // Esto asegura que solo exista una instancia del reproductor activa en la pila
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun guardarEnHistorial(cancion: Cancion) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val historyJson = prefs.getString(KEY_HISTORY_JSON, null)

        val listType = object : TypeToken<MutableList<Cancion>>() {}.type
        val historial: MutableList<Cancion> = if (historyJson != null) {
            try {
                gson.fromJson(historyJson, listType)
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        historial.removeAll { it.titulo == cancion.titulo || (it.id != null && it.id == cancion.id) }
        historial.add(0, cancion)

        if (historial.size > 20) {
            historial.removeAt(historial.size - 1)
        }

        val updatedJson = gson.toJson(historial)
        prefs.edit().putString(KEY_HISTORY_JSON, updatedJson).apply()
    }

    private fun cargarHistorialYRecomendaciones() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val historyJson = prefs.getString(KEY_HISTORY_JSON, null)

        var historial: List<Cancion> = emptyList()
        if (historyJson != null) {
            val listType = object : TypeToken<List<Cancion>>() {}.type
            try {
                historial = gson.fromJson(historyJson, listType)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (historial.isNotEmpty()) {
            listaCancionesActuales = historial
            tvSeccionTitulo.text = "Escuchadas recientemente"
            adapter.actualizarLista(historial)
        } else {
            // Cargar canciones recomendadas del servidor
            val api = obtenerApiService()
            if (api != null) {
                tvSeccionTitulo.text = "Cargando canciones más sonadas..."
                progressBarMain.visibility = View.VISIBLE
                lifecycleScope.launch {
                    try {
                        val respuesta = api.obtenerCancionesDescargadas()
                        if (respuesta.canciones.isNotEmpty()) {
                            listaCancionesActuales = respuesta.canciones
                            tvSeccionTitulo.text = "🔥 Canciones más sonadas"
                            adapter.actualizarLista(respuesta.canciones)
                        } else {
                            tvSeccionTitulo.text = "🔍 ¿Qué quieres escuchar?"
                            adapter.actualizarLista(emptyList())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        tvSeccionTitulo.text = "🔍 ¿Qué quieres escuchar?"
                        adapter.actualizarLista(emptyList())
                    } finally {
                        progressBarMain.visibility = View.GONE
                    }
                }
            } else {
                tvSeccionTitulo.text = "🔍 ¿Qué quieres escuchar?"
                adapter.actualizarLista(emptyList())
            }
        }
    }

    private var cacheAlbumesPopulares: List<Playlist>? = null

    private fun cargarAlbumes(query: String) {
        ocultarSugerenciasYTeclado()
        val api = obtenerApiService()
        if (api == null) {
            tvSeccionTitulo.text = "Sin conexión"
            adapter.actualizarLista(emptyList())
            return
        }

        val rvResultados = findViewById<RecyclerView>(R.id.rv_resultados)
        rvResultados?.adapter = playlistAdapter

        if (query.isEmpty() && !cacheAlbumesPopulares.isNullOrEmpty()) {
            tvSeccionTitulo.text = "Álbumes"
            playlistAdapter.actualizarLista(cacheAlbumesPopulares!!)
            progressBarMain.visibility = View.GONE
            return
        }

        val terminoBusqueda = if (query.isNotEmpty()) query else "album popular completo exitos"
        tvSeccionTitulo.text = if (query.isNotEmpty()) "Álbumes de \"$query\"" else "Álbumes"
        progressBarMain.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val respuesta = api.buscarPlaylists(terminoBusqueda)
                if (respuesta.playlists.isNotEmpty()) {
                    if (query.isEmpty()) {
                        cacheAlbumesPopulares = respuesta.playlists
                    }
                    tvSeccionTitulo.text = if (query.isNotEmpty()) "Álbumes de \"$query\" (${respuesta.playlists.size})" else "Álbumes"
                    playlistAdapter.actualizarLista(respuesta.playlists)
                } else {
                    val respuestaCanciones = api.buscarCancion("$terminoBusqueda completo")
                    val albumes = respuestaCanciones.canciones.filter { cancion ->
                        val t = (cancion.titulo ?: "").lowercase()
                        t.contains("album") || t.contains("álbum") || t.contains("completo") ||
                        t.contains("discografia") || t.contains("mix") || t.contains("playlist")
                    }
                    if (albumes.isNotEmpty()) {
                        rvResultados?.adapter = adapter
                        listaCancionesActuales = albumes
                        tvSeccionTitulo.text = "Álbumes de artistas"
                        adapter.actualizarLista(albumes)
                    } else {
                        Toast.makeText(this@MainActivity, "No se encontraron álbumes", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tvSeccionTitulo.text = "Error cargando álbumes"
            } finally {
                progressBarMain.visibility = View.GONE
            }
        }
    }

    private fun cargarCancionesDePlaylist(playlist: Playlist) {
        val playlistUrl = playlist.url ?: return
        val rvResultados = findViewById<RecyclerView>(R.id.rv_resultados)
        rvResultados?.adapter = adapter // Volver a mostrar adaptador de canciones

        if (playlistUrl.startsWith("custom:")) {
            val customId = playlistUrl.removePrefix("custom:")
            val customPlaylists = LocalMusicManager.obtenerPlaylistsPersonalizadas(this)
            val found = customPlaylists.find { it.id == customId }
            if (found != null && found.canciones.isNotEmpty()) {
                listaCancionesActuales = found.canciones
                tvSeccionTitulo.text = "📂 ${found.nombre} (${found.canciones.size})"
                adapter.actualizarLista(found.canciones)
            } else {
                Toast.makeText(this, "Esta playlist aún está vacía. ¡Agrégale canciones!", Toast.LENGTH_SHORT).show()
                rvResultados?.adapter = playlistAdapter
            }
            return
        }

        progressBarMain.visibility = View.VISIBLE
        tvSeccionTitulo.text = "💿 Cargando canciones..."
        adapter.actualizarLista(emptyList())

        val api = obtenerApiService()
        if (api != null) {
            lifecycleScope.launch {
                try {
                    val respuesta = api.obtenerCancionesPlaylist(playlistUrl)
                    if (respuesta.canciones.isNotEmpty()) {
                        listaCancionesActuales = respuesta.canciones
                        tvSeccionTitulo.text = "💿 ${playlist.titulo} (${respuesta.canciones.size})"
                        adapter.actualizarLista(respuesta.canciones)
                    } else {
                        Toast.makeText(this@MainActivity, "No se encontraron canciones en esta playlist", Toast.LENGTH_SHORT).show()
                        // Restaurar lista de playlists
                        rvResultados?.adapter = playlistAdapter
                        tvSeccionTitulo.text = "💿 Albunes de artistas"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "Error al cargar canciones del álbum", Toast.LENGTH_SHORT).show()
                    rvResultados?.adapter = playlistAdapter
                    tvSeccionTitulo.text = "💿 Albunes de artistas"
                } finally {
                    progressBarMain.visibility = View.GONE
                }
            }
        }
    }

    private fun cargarVerTodoMasEscuchadas(titulo: String) {
        val etBusqueda = findViewById<EditText>(R.id.et_busqueda)
        cambiarPestaña(TAB_NUBE, etBusqueda)
        val rvResultados = findViewById<RecyclerView>(R.id.rv_resultados)
        rvResultados?.adapter = adapter
        tvSeccionTitulo.text = titulo
        progressBarMain.visibility = View.VISIBLE
        
        val api = obtenerApiService()
        if (api != null) {
            lifecycleScope.launch {
                try {
                    val res = api.obtenerMasEscuchadas(30)
                    if (res.canciones.isNotEmpty()) {
                        listaCancionesActuales = res.canciones
                        adapter.actualizarLista(res.canciones)
                        tvResultadosCounter.text = "${res.canciones.size} canciones"
                        tvResultadosCounter.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(this@MainActivity, "No se encontraron canciones", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    progressBarMain.visibility = View.GONE
                }
            }
        } else {
            progressBarMain.visibility = View.GONE
        }
    }

    private fun cargarVerTodoNuevas(titulo: String) {
        val etBusqueda = findViewById<EditText>(R.id.et_busqueda)
        cambiarPestaña(TAB_NUBE, etBusqueda)
        val rvResultados = findViewById<RecyclerView>(R.id.rv_resultados)
        rvResultados?.adapter = adapter
        tvSeccionTitulo.text = titulo
        progressBarMain.visibility = View.VISIBLE
        
        val api = obtenerApiService()
        if (api != null) {
            lifecycleScope.launch {
                try {
                    val res = api.obtenerNuevosLanzamientos(30)
                    if (res.canciones.isNotEmpty()) {
                        listaCancionesActuales = res.canciones
                        adapter.actualizarLista(res.canciones)
                        tvResultadosCounter.text = "${res.canciones.size} canciones"
                        tvResultadosCounter.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(this@MainActivity, "No se encontraron canciones", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    progressBarMain.visibility = View.GONE
                }
            }
        } else {
            progressBarMain.visibility = View.GONE
        }
    }

    private fun inicializarDashboard() {
        val rvRec = findViewById<RecyclerView>(R.id.rv_dash_recomendadas)
        val rvPop = findViewById<RecyclerView>(R.id.rv_dash_populares)
        val rvNuevas = findViewById<RecyclerView>(R.id.rv_dash_lanzamientos)
        val cvBuscador = findViewById<View>(R.id.cv_dashboard_buscador)
        val etBusqueda = findViewById<EditText>(R.id.et_busqueda)

        cvBuscador?.setOnClickListener {
            val navBuscar = findViewById<View>(R.id.nav_buscar)
            navBuscar?.performClick()
        }

        findViewById<View>(R.id.card_dash_canciones)?.setOnClickListener {
            val navBuscar = findViewById<View>(R.id.nav_buscar)
            navBuscar?.performClick()
        }
        findViewById<View>(R.id.card_dash_artistas)?.setOnClickListener {
            val navBuscar = findViewById<View>(R.id.nav_buscar)
            navBuscar?.performClick()
        }
        findViewById<View>(R.id.card_dash_albumes)?.setOnClickListener {
            cambiarPestaña(TAB_ALBUMES, etBusqueda)
        }
        findViewById<View>(R.id.card_dash_favoritas)?.setOnClickListener {
            cambiarPestaña(TAB_FAVORITAS, etBusqueda)
        }

        // Configurar clics en tarjetas de género musical
        findViewById<View>(R.id.card_genero_reggaeton)?.setOnClickListener { cargarCancionesPorGenero("reggaeton", "Reggaetón & Urbano") }
        findViewById<View>(R.id.card_genero_rock)?.setOnClickListener { cargarCancionesPorGenero("rock", "Rock & Alternativo") }
        findViewById<View>(R.id.card_genero_pop)?.setOnClickListener { cargarCancionesPorGenero("pop", "Pop Hits") }
        findViewById<View>(R.id.card_genero_latin)?.setOnClickListener { cargarCancionesPorGenero("latin", "Música Latina & Salsa") }
        findViewById<View>(R.id.card_genero_electro)?.setOnClickListener { cargarCancionesPorGenero("electro", "Electro & EDM") }
        findViewById<View>(R.id.card_genero_reggae)?.setOnClickListener { cargarCancionesPorGenero("reggae", "Reggae & Dub") }
        findViewById<View>(R.id.card_genero_hiphop)?.setOnClickListener { cargarCancionesPorGenero("hiphop", "Hip Hop & Trap") }
        findViewById<View>(R.id.card_genero_cumbia)?.setOnClickListener { cargarCancionesPorGenero("cumbia", "Cumbia & Tropical") }

        findViewById<View>(R.id.tv_dash_ver_todo_rec)?.setOnClickListener {
            cargarVerTodoNuevas("Recomendadas para ti")
        }
        findViewById<View>(R.id.tv_dash_ver_todo_pop)?.setOnClickListener {
            cargarVerTodoMasEscuchadas("Las más escuchadas")
        }
        findViewById<View>(R.id.tv_dash_ver_todo_nuevas)?.setOnClickListener {
            cargarVerTodoNuevas("Nuevos lanzamientos")
        }
        // Configure adapters
        adapterDashboardRec = DashboardHorizontalAdapter(emptyList(), showNewBadge = false) { cancion, pos ->
            reproducirDesdeDashboard(adapterDashboardRec.canciones, pos)
        }
        adapterDashboardPop = DashboardRankedAdapter(emptyList(), onClick = { cancion, pos ->
            reproducirDesdeDashboard(adapterDashboardPop.canciones, pos)
        }, onOptionsClick = { cancion ->
            marcarFavoritaYDescargar(cancion)
        })
        adapterDashboardNuevas = DashboardHorizontalAdapter(emptyList(), showNewBadge = true) { cancion, pos ->
            reproducirDesdeDashboard(adapterDashboardNuevas.canciones, pos)
        }

        rvRec?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvRec?.adapter = adapterDashboardRec

        rvPop?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvPop?.adapter = adapterDashboardPop

        rvNuevas?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvNuevas?.adapter = adapterDashboardNuevas
    }

    private fun cargarCancionesPorGenero(generoKey: String, titulo: String) {
        val etBusqueda = findViewById<EditText>(R.id.et_busqueda)
        cambiarPestaña(TAB_NUBE, etBusqueda)
        val rvResultados = findViewById<RecyclerView>(R.id.rv_resultados)
        rvResultados?.adapter = adapter
        tvSeccionTitulo.text = titulo
        progressBarMain.visibility = View.VISIBLE
        
        val api = obtenerApiService()
        if (api != null) {
            lifecycleScope.launch {
                try {
                    val res = api.obtenerPlaylistGenero(generoKey, 30)
                    if (res.canciones.isNotEmpty()) {
                        listaCancionesActuales = res.canciones
                        adapter.actualizarLista(res.canciones)
                        tvResultadosCounter.text = "${res.canciones.size} canciones"
                        tvResultadosCounter.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(this@MainActivity, "No se encontraron canciones para este género", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "Error al cargar playlist del género", Toast.LENGTH_SHORT).show()
                } finally {
                    progressBarMain.visibility = View.GONE
                }
            }
        } else {
            progressBarMain.visibility = View.GONE
        }
    }
    private fun reproducirDesdeDashboard(lista: List<Cancion>, pos: Int) {
        if (lista.isNotEmpty() && pos in lista.indices) {
            val cancion = lista[pos]
            PlayerManager.establecerListaReproduccion(lista, pos)
            reproducirCancionSeleccionada(cancion)
        }
    }

    private fun cargarDatosDashboard() {
        val tvGreeting = findViewById<TextView>(R.id.tv_dashboard_greeting)
        val tvCancionesCount = findViewById<TextView>(R.id.tv_dash_canciones_count)
        val tvArtistasCount = findViewById<TextView>(R.id.tv_dash_artistas_count)
        val tvAlbumesCount = findViewById<TextView>(R.id.tv_dash_albumes_count)
        val tvFavoritasCount = findViewById<TextView>(R.id.tv_dash_favoritas_count)

        // Set user greeting dynamically from registration
        val nombreUsuario = UserAuthManager.obtenerNombreUsuario(this)
        tvGreeting?.text = "¡Hola, $nombreUsuario! 👋"

        // Set local stats
        val favs = LocalMusicManager.obtenerFavoritos(this)
        tvFavoritasCount?.text = favs.size.toString()

        val api = obtenerApiService()
        if (api != null) {
            lifecycleScope.launch {
                try {
                    // 1. Obtener estadísticas de canciones descargadas del servidor en tiempo real
                    val respuesta = api.obtenerCancionesDescargadas()
                    val canciones = respuesta.canciones
                    if (canciones.isNotEmpty()) {
                        tvCancionesCount?.text = canciones.size.toString()
                        
                        val uniqueArtists = canciones.mapNotNull { it.canal }.distinct().size
                        tvArtistasCount?.text = uniqueArtists.toString()

                        val albums = canciones.filter { cancion ->
                            val t = (cancion.titulo ?: "").lowercase()
                            t.contains("album") || t.contains("álbum") || t.contains("completo") || t.contains("mix")
                        }
                        tvAlbumesCount?.text = albums.size.toString()

                        // Recomendadas: Mostrar las 6 primeras del servidor en caché
                        val recSongs = canciones.take(6)
                        adapterDashboardRec.actualizarLista(recSongs)
                    }

                    // 2. Cargar de VERDAD las más escuchadas globales desde YouTube (Sin mezclar con historial local)
                    try {
                        val respuestaTendencias = api.obtenerMasEscuchadas(10)
                        val popSongs = if (respuestaTendencias.canciones.isNotEmpty()) {
                            respuestaTendencias.canciones.take(5)
                        } else {
                            api.buscarCancion("exitos musica tendencias del momento youtube").canciones.take(5)
                        }
                        if (popSongs.isNotEmpty()) {
                            adapterDashboardPop.actualizarLista(popSongs)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 3. Cargar de VERDAD los nuevos lanzamientos musicales desde YouTube
                    try {
                        val respuestaNuevas = api.obtenerNuevosLanzamientos(10)
                        val newSongs = if (respuestaNuevas.canciones.isNotEmpty()) {
                            respuestaNuevas.canciones.take(6)
                        } else {
                            api.buscarCancion("nuevos lanzamientos estrenos canciones oficiales youtube").canciones.take(6)
                        }
                        if (newSongs.isNotEmpty()) {
                            adapterDashboardNuevas.actualizarLista(newSongs)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
