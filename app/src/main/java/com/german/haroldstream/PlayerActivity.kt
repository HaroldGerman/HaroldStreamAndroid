package com.german.haroldstream

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import android.content.Intent
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class PlayerActivity : AppCompatActivity(), PlayerManager.PlayerStateListener {

    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPlayerStar: ImageButton
    private lateinit var btnDownloadMobile: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton

    private var currentCancionLocal: Cancion? = null
    private var isShuffleOn = false
    private var isRepeatOn = false

    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    companion object {
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_ORIGINAL_URL = "extra_original_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_THUMBNAIL = "extra_thumbnail"
        const val EXTRA_CANAL = "extra_canal"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val btnCollapse = findViewById<ImageButton>(R.id.btn_collapse)
        val ivCover = findViewById<ImageView>(R.id.iv_player_cover)
        val tvTitle = findViewById<TextView>(R.id.tv_player_title)
        val tvArtist = findViewById<TextView>(R.id.tv_player_artist)

        seekBar = findViewById(R.id.sb_progress)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnPlayerStar = findViewById(R.id.btn_player_star)
        btnShuffle = findViewById(R.id.btn_shuffle)
        btnRepeat = findViewById(R.id.btn_repeat)

        val btnRewind = findViewById<ImageButton>(R.id.btn_rewind)
        val btnForward = findViewById<ImageButton>(R.id.btn_forward)
        btnDownloadMobile = findViewById(R.id.btn_download_mobile)

        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
        val originalUrl = intent.getStringExtra(EXTRA_ORIGINAL_URL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "TushNH Audio"
        val thumbnail = intent.getStringExtra(EXTRA_THUMBNAIL)
        val canal = intent.getStringExtra(EXTRA_CANAL) ?: "Desconocido"

        currentCancionLocal = Cancion(
            titulo = title,
            thumbnail = thumbnail,
            canal = canal,
            url = originalUrl ?: streamUrl
        )

        tvTitle.text = title
        tvArtist.text = canal

        ivCover.load(thumbnail) {
            crossfade(true)
            placeholder(android.R.drawable.ic_media_play)
            error(android.R.drawable.ic_media_play)
        }

        actualizarEstadoEstrellaYDescarga()

        btnPlayerStar.setOnClickListener {
            currentCancionLocal?.let { cancion ->
                val esFav = LocalMusicManager.esFavorito(this, cancion)
                if (!esFav) {
                    LocalMusicManager.guardarFavorito(this, cancion)
                    if (!streamUrl.isNullOrEmpty()) {
                        LocalMusicManager.descargarMP3EnCelular(this, streamUrl, title)
                    }
                    Toast.makeText(this, "✓ Guardada en tu biblioteca y descargada 📥", Toast.LENGTH_SHORT).show()
                } else {
                    LocalMusicManager.quitarFavorito(this, cancion)
                    Toast.makeText(this, "Removida de tu biblioteca", Toast.LENGTH_SHORT).show()
                }
                actualizarEstadoEstrellaYDescarga()
            }
        }

        btnCollapse.setOnClickListener {
            finish()
        }

        // ---- SHUFFLE ----
        btnShuffle.setOnClickListener {
            isShuffleOn = !isShuffleOn
            PlayerManager.isShuffleOn = isShuffleOn
            if (isShuffleOn) {
                btnShuffle.setColorFilter(android.graphics.Color.parseColor("#7356F1"))
                Toast.makeText(this, "🔀 Aleatorio activado", Toast.LENGTH_SHORT).show()
            } else {
                btnShuffle.clearColorFilter()
                btnShuffle.setColorFilter(android.graphics.Color.parseColor("#788295"))
                Toast.makeText(this, "Aleatorio desactivado", Toast.LENGTH_SHORT).show()
            }
        }

        // ---- REPEAT ----
        btnRepeat.setOnClickListener {
            isRepeatOn = !isRepeatOn
            PlayerManager.isRepeatOn = isRepeatOn
            if (isRepeatOn) {
                btnRepeat.setColorFilter(android.graphics.Color.parseColor("#7356F1"))
                PlayerManager.player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                Toast.makeText(this, "🔁 Repetir canción activado", Toast.LENGTH_SHORT).show()
            } else {
                btnRepeat.clearColorFilter()
                btnRepeat.setColorFilter(android.graphics.Color.parseColor("#788295"))
                PlayerManager.player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                Toast.makeText(this, "Repetir desactivado", Toast.LENGTH_SHORT).show()
            }
        }

        // Sincronizar estado guardado de shuffle/repeat
        isShuffleOn = PlayerManager.isShuffleOn
        isRepeatOn = PlayerManager.isRepeatOn
        if (isShuffleOn) btnShuffle.setColorFilter(android.graphics.Color.parseColor("#7356F1"))
        else btnShuffle.setColorFilter(android.graphics.Color.parseColor("#788295"))
        if (isRepeatOn) btnRepeat.setColorFilter(android.graphics.Color.parseColor("#7356F1"))
        else btnRepeat.setColorFilter(android.graphics.Color.parseColor("#788295"))

        val btnShare = findViewById<ImageButton>(R.id.btn_share)
        btnShare?.setOnClickListener {
            currentCancionLocal?.let { cancion ->
                mostrarBottomSheetCompartir(cancion)
            }
        }

        val btnQueue = findViewById<ImageButton>(R.id.btn_queue)
        btnQueue?.setOnClickListener {
            mostrarBottomSheetQueue()
        }

        val btnAddPlaylist = findViewById<ImageButton>(R.id.btn_add_playlist)
        btnAddPlaylist?.setOnClickListener {
            currentCancionLocal?.let { cancion ->
                mostrarBottomSheetAñadirAPlaylist(cancion)
            }
        }

        val btnLyrics = findViewById<ImageButton>(R.id.btn_lyrics)
        btnLyrics?.setOnClickListener {
            mostrarBottomSheetLetras()
        }

        val p = PlayerManager.getOrCreatePlayer(this)

        if (!streamUrl.isNullOrEmpty() && (PlayerManager.currentStreamUrl != streamUrl || !p.isPlaying)) {
            PlayerManager.playCancion(this, currentCancionLocal!!, streamUrl)
        }

        actualizarTiempos(p)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    PlayerManager.seekTo(it.progress.toLong())
                }
                isUserSeeking = false
            }
        })

        btnPlayPause.setOnClickListener {
            PlayerManager.togglePlayPause()
        }

        btnRewind.setOnClickListener {
            PlayerManager.anteriorCancion(this)
        }

        btnForward.setOnClickListener {
            if (isRepeatOn) {
                // Si está en modo repeat, reiniciar canción actual
                PlayerManager.seekTo(0L)
                PlayerManager.player?.play()
            } else {
                PlayerManager.siguienteCancion(this)
            }
        }

        btnDownloadMobile.setOnClickListener {
            val urlADescargar = PlayerManager.currentStreamUrl ?: streamUrl
            if (!urlADescargar.isNullOrEmpty()) {
                LocalMusicManager.descargarMP3EnCelular(this, urlADescargar, title)
                Toast.makeText(this, "Descargando '$title' en tu celular 📥", Toast.LENGTH_LONG).show()
                actualizarEstadoEstrellaYDescarga()
            } else {
                Toast.makeText(this, "URL no disponible para descargar", Toast.LENGTH_SHORT).show()
            }
        }

        updateSeekBarRunnable.run()
    }

    private fun actualizarEstadoEstrellaYDescarga() {
        currentCancionLocal?.let { cancion ->
            val esFav = LocalMusicManager.esFavorito(this, cancion)
            if (esFav) {
                btnPlayerStar.setImageResource(R.drawable.ic_heart_solid)
                btnPlayerStar.setColorFilter(android.graphics.Color.parseColor("#a855f7"))
            } else {
                btnPlayerStar.setImageResource(R.drawable.ic_heart_outline)
                btnPlayerStar.setColorFilter(android.graphics.Color.parseColor("#31124a"))
            }

            val yaDescargado = LocalMusicManager.esDescargadoLocalmente(this, cancion)
            if (yaDescargado) {
                btnDownloadMobile.visibility = View.GONE
            } else {
                btnDownloadMobile.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PlayerManager.addListener(this)
        val p = PlayerManager.player
        if (p != null) {
            onIsPlayingChanged(p.isPlaying)
            actualizarTiempos(p)
        }
    }

    override fun onPause() {
        super.onPause()
        PlayerManager.removeListener(this)
    }

    override fun onSongChanged(cancion: Cancion?) {
        cancion?.let {
            currentCancionLocal = it
            findViewById<TextView>(R.id.tv_player_title).text = it.titulo ?: "Canción"
            findViewById<TextView>(R.id.tv_player_artist).text = it.canal ?: "Desconocido"
            findViewById<ImageView>(R.id.iv_player_cover).load(it.thumbnail) {
                crossfade(true)
            }
            actualizarEstadoEstrellaYDescarga()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun onPlaybackReady(durationMs: Long) {
        if (durationMs > 0) {
            seekBar.max = durationMs.toInt()
            tvTotalTime.text = formatTime(durationMs)
        }
    }

    private fun actualizarTiempos(p: ExoPlayer) {
        val duration = p.duration
        if (duration > 0) {
            seekBar.max = duration.toInt()
            tvTotalTime.text = formatTime(duration)
        }
        val currentPosition = p.currentPosition
        seekBar.progress = currentPosition.toInt()
        tvCurrentTime.text = formatTime(currentPosition)
    }

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            val p = PlayerManager.player
            if (p != null && p.isPlaying && !isUserSeeking) {
                val currentPosition = p.currentPosition
                seekBar.progress = currentPosition.toInt()
                tvCurrentTime.text = formatTime(currentPosition)
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // =====================================================================
    // NUEVO BOTTOM SHEET DE COMPARTIR - estilo premium como la foto
    // =====================================================================
    private fun mostrarBottomSheetCompartir(cancion: Cancion) {
        val p = PlayerManager.player
        val durationMs = p?.duration ?: 0L
        val durationSec = if (durationMs > 0) (durationMs / 1000).toInt() else 240 // 4 mins default
        val currentSec = if (p != null) (p.currentPosition / 1000).toInt() else 0

        // Límites ultra seguros
        val safeCurrentSec = currentSec.coerceIn(0, durationSec)
        val defaultEnd = (safeCurrentSec + 15).coerceAtMost(durationSec)

        val bottomSheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_share_bottom, null)
        bottomSheet.setContentView(view)
        bottomSheet.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        val etStart = view.findViewById<EditText>(R.id.et_share_start)
        val etEnd = view.findViewById<EditText>(R.id.et_share_end)
        val tvDuracion = view.findViewById<TextView>(R.id.tv_share_duracion)
        val rangeSlider = view.findViewById<com.google.android.material.slider.RangeSlider>(R.id.rs_share_range)

        etStart.setText(formatTimeShort(safeCurrentSec))
        etEnd.setText(formatTimeShort(defaultEnd))
        tvDuracion.text = "Duración: ${formatTimeShort(defaultEnd - safeCurrentSec)}"

        // Configurar RangeSlider con el rango completo de la canción
        if (rangeSlider != null) {
            rangeSlider.valueFrom = 0.0f
            val maxVal = durationSec.toFloat().coerceAtLeast(1.0f)
            rangeSlider.valueTo = maxVal
            
            val val1 = safeCurrentSec.toFloat().coerceIn(0.0f, maxVal)
            val val2 = defaultEnd.toFloat().coerceIn(val1, maxVal)
            rangeSlider.values = listOf(val1, val2)
        }

        // Actualizar duración al cambiar los campos de texto y actualizar RangeSlider
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val st = parseTimeInput(etStart.text.toString())
                val en = parseTimeInput(etEnd.text.toString())
                if (en > st) {
                    tvDuracion.text = "Duración: ${formatTimeShort(en - st)}"
                    if (rangeSlider != null) {
                        val safeSt = st.toFloat().coerceIn(rangeSlider.valueFrom, rangeSlider.valueTo)
                        val safeEn = en.toFloat().coerceIn(rangeSlider.valueFrom, rangeSlider.valueTo)
                        if (safeEn > safeSt) {
                            rangeSlider.values = listOf(safeSt, safeEn)
                        }
                    }
                }
            }
        }
        etStart.addTextChangedListener(watcher)
        etEnd.addTextChangedListener(watcher)

        // Evento al deslizar cualquiera de los dos puntos del RangeSlider
        rangeSlider?.addOnChangeListener { slider, _, fromUser ->
            if (fromUser) {
                val values = slider.values
                val startVal = values[0].toInt()
                val endVal = values[1].toInt()

                etStart.removeTextChangedListener(watcher)
                etEnd.removeTextChangedListener(watcher)

                etStart.setText(formatTimeShort(startVal))
                etEnd.setText(formatTimeShort(endVal))
                tvDuracion.text = "Duración: ${formatTimeShort(endVal - startVal)}"

                etStart.addTextChangedListener(watcher)
                etEnd.addTextChangedListener(watcher)
            }
        }

        // Botón Compartir Enlace
        view.findViewById<LinearLayout>(R.id.btn_share_enlace).setOnClickListener {
            bottomSheet.dismiss()
            val baseUrl = if (MainActivity.DEFAULT_URL.endsWith("/")) MainActivity.DEFAULT_URL else "${MainActivity.DEFAULT_URL}/"
            val shareUrl = "${baseUrl}compartir?title=${Uri.encode(cancion.titulo ?: "")}&thumb=${Uri.encode(cancion.thumbnail ?: "")}&canal=${Uri.encode(cancion.canal ?: "")}&url=${Uri.encode(cancion.url ?: "")}"
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "🎵 Escucha *${cancion.titulo}* gratis en HaroldStream:\n\n$shareUrl")
            }
            startActivity(Intent.createChooser(sendIntent, "Compartir enlace en..."))
        }

        // Botón Estado Audio
        view.findViewById<LinearLayout>(R.id.btn_share_estado).setOnClickListener {
            bottomSheet.dismiss()
            val start = parseTimeInput(etStart.text.toString())
            val end = parseTimeInput(etEnd.text.toString())
            if (start >= 0 && end > start) descargarYCompartirClip(cancion, start, end, true)
            else Toast.makeText(this, "Tiempo inválido", Toast.LENGTH_SHORT).show()
        }

        // Botón Clip Video
        view.findViewById<LinearLayout>(R.id.btn_share_clip).setOnClickListener {
            bottomSheet.dismiss()
            val start = parseTimeInput(etStart.text.toString())
            val end = parseTimeInput(etEnd.text.toString())
            if (start >= 0 && end > start) descargarYCompartirClip(cancion, start, end, false)
            else Toast.makeText(this, "Tiempo inválido", Toast.LENGTH_SHORT).show()
        }

        // Botón Cancelar
        view.findViewById<LinearLayout>(R.id.btn_share_cancelar).setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun formatTimeShort(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun parseTimeInput(text: String): Int {
        // Acepta tanto "75" (segundos) como "01:15" (mm:ss)
        return if (text.contains(":")) {
            val parts = text.split(":")
            val m = parts[0].toIntOrNull() ?: 0
            val s = parts[1].toIntOrNull() ?: 0
            m * 60 + s
        } else {
            text.toIntOrNull() ?: 0
        }
    }

    private fun descargarYCompartirClip(cancion: Cancion, start: Int, end: Int, isAudioOnly: Boolean) {
        val originalUrl = cancion.url ?: ""
        if (originalUrl.isEmpty()) {
            Toast.makeText(this, "URL original de YouTube no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = android.app.ProgressDialog(this).apply {
            setTitle("Procesando clip...")
            setMessage("Descargando fragmento desde el servidor...")
            setCancelable(false)
            show()
        }

        val devId = UserAuthManager.obtenerDeviceId(this)
        val baseUrl = if (MainActivity.DEFAULT_URL.endsWith("/")) MainActivity.DEFAULT_URL else "${MainActivity.DEFAULT_URL}/"
        val endpoint = if (isAudioOnly) {
            "${baseUrl}api/recortar-audio-portada?url=${Uri.encode(originalUrl)}&thumb=${Uri.encode(cancion.thumbnail ?: "")}&start=$start&end=$end&title=${Uri.encode(cancion.titulo ?: "")}&artist=${Uri.encode(cancion.canal ?: "")}&deviceId=$devId"
        } else {
            "${baseUrl}api/recortar-video?url=${Uri.encode(originalUrl)}&start=$start&end=$end&deviceId=$devId"
        }

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        val tempFile = java.io.File(cacheDir, if (isAudioOnly) "HaroldStream_audio.mp4" else "HaroldStream_video.mp4")
                        if (tempFile.exists()) tempFile.delete()

                        tempFile.outputStream().use { output ->
                            body.byteStream().copyTo(output)
                        }

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            progressDialog.dismiss()
                            lanzarIntentCompartirArchivo(tempFile)
                        }
                    } else {
                        throw Exception("Cuerpo de respuesta vacío")
                    }
                } else {
                    throw Exception("Código de error del servidor: ${response.code}")
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@PlayerActivity, "Error al procesar fragmento: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun lanzarIntentCompartirArchivo(file: java.io.File) {
        try {
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "com.german.haroldstream.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Compartir fragmento en...")
            startActivity(chooser)

        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir archivo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarBottomSheetQueue() {
        val bottomSheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_queue, null)
        bottomSheet.setContentView(view)

        val rvQueue = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_queue)
        val tvTitle = view.findViewById<TextView>(R.id.tv_queue_title)

        val lista = PlayerManager.listaReproduccion
        if (lista.isEmpty()) {
            tvTitle.text = "No hay canciones en la lista"
            rvQueue.visibility = View.GONE
        } else {
            tvTitle.text = "Siguiente en la lista (${lista.size})"
            rvQueue.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            
            val queueAdapter = CancionAdapter(lista, onClick = { cancionSelected ->
                val posicion = PlayerManager.listaReproduccion.indexOf(cancionSelected)
                if (posicion != -1) {
                    PlayerManager.establecerListaReproduccion(PlayerManager.listaReproduccion, posicion)
                    PlayerManager.onAutoPlayNextListener?.invoke(cancionSelected)
                    bottomSheet.dismiss()
                }
            }, onFavoriteToggle = { cancion, nuevoEstado ->
                if (nuevoEstado) {
                    LocalMusicManager.guardarFavorito(this, cancion)
                } else {
                    LocalMusicManager.quitarFavorito(this, cancion)
                }
            })
            rvQueue.adapter = queueAdapter
            
            // Hacer scroll hasta la canción que se está reproduciendo actualmente
            val currentPos = PlayerManager.indiceActual
            if (currentPos in lista.indices) {
                rvQueue.scrollToPosition(currentPos)
            }
        }

        bottomSheet.show()
    }

    private fun limpiarLrcParaMostrar(texto: String): String {
        return texto.lines().map { line ->
            line.replace(Regex("\\[\\d{2}:\\d{2}(?:\\.\\d{2,3})?\\]"), "").trim()
        }.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun mostrarBottomSheetLetras() {
        val cancion = currentCancionLocal ?: return
        val title = cancion.titulo ?: "Canción"
        val canal = cancion.canal ?: "Artista"

        val dialogView = layoutInflater.inflate(R.layout.layout_bottom_sheet_lyrics, null)
        val bottomSheet = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        bottomSheet.setContentView(dialogView)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_lyrics_title)
        val tvLyricsContent = dialogView.findViewById<TextView>(R.id.tv_lyrics_content)
        val containerLines = dialogView.findViewById<LinearLayout>(R.id.container_lyrics_lines)
        val svScroll = dialogView.findViewById<androidx.core.widget.NestedScrollView>(R.id.sv_lyrics_scroll)
        val progressBar = dialogView.findViewById<android.widget.ProgressBar>(R.id.pb_lyrics_loading)

        tvTitle.text = "🎤 Letra: $title"
        progressBar.visibility = View.VISIBLE
        tvLyricsContent.visibility = View.GONE

        var lyricsJob: kotlinx.coroutines.Job? = null

        lifecycleScope.launch {
            val fetched = LyricsManager.fetchOnlineLyrics(title, canal)
            progressBar.visibility = View.GONE
            
            if (!fetched.isNullOrBlank()) {
                val parsedLines = LyricsManager.parseLrc(fetched)
                if (parsedLines.isNotEmpty()) {
                    tvLyricsContent.visibility = View.GONE
                    containerLines.removeAllViews()

                    val textViews = mutableListOf<TextView>()
                    val activeBg = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 24f
                        colors = intArrayOf(
                            android.graphics.Color.parseColor("#7356F1"),
                            android.graphics.Color.parseColor("#9E6CFF")
                        )
                    }

                    for (item in parsedLines) {
                        val tv = TextView(this@PlayerActivity).apply {
                            text = item.text
                            textSize = 16f
                            setTextColor(android.graphics.Color.parseColor("#80FFFFFF"))
                            setPadding(28, 16, 28, 16)
                            gravity = android.view.Gravity.CENTER
                            isClickable = true
                            isFocusable = true
                            setOnClickListener {
                                PlayerManager.player?.seekTo(item.timeMs)
                            }
                        }
                        containerLines.addView(tv)
                        textViews.add(tv)
                    }

                    lyricsJob = lifecycleScope.launch {
                        var lastActiveIdx = -1
                        while (isActive && bottomSheet.isShowing) {
                            val playerPos = PlayerManager.player?.currentPosition ?: 0L
                            var currentIdx = -1

                            for (i in parsedLines.indices) {
                                if (playerPos >= parsedLines[i].timeMs) {
                                    currentIdx = i
                                } else {
                                    break
                                }
                            }

                            if (currentIdx != -1 && currentIdx != lastActiveIdx) {
                                lastActiveIdx = currentIdx
                                for (i in textViews.indices) {
                                    val tv = textViews[i]
                                    if (i == currentIdx) {
                                        tv.setTextColor(android.graphics.Color.WHITE)
                                        tv.textSize = 19f
                                        tv.setTypeface(null, android.graphics.Typeface.BOLD)
                                        tv.background = activeBg
                                        svScroll.smoothScrollTo(0, tv.top - (svScroll.height / 2) + (tv.height / 2))
                                    } else {
                                        tv.setTextColor(android.graphics.Color.parseColor("#80FFFFFF"))
                                        tv.textSize = 15f
                                        tv.setTypeface(null, android.graphics.Typeface.NORMAL)
                                        tv.background = null
                                    }
                                }
                            }
                            delay(150)
                        }
                    }
                } else {
                    tvLyricsContent.visibility = View.VISIBLE
                    tvLyricsContent.text = limpiarLrcParaMostrar(fetched)
                }
            } else {
                tvLyricsContent.visibility = View.VISIBLE
                tvLyricsContent.text = "No se encontró la letra para esta canción."
            }
        }

        bottomSheet.setOnDismissListener {
            lyricsJob?.cancel()
        }

        bottomSheet.show()
    }

    private fun mostrarBottomSheetAñadirAPlaylist(cancion: Cancion) {
        val playlists = LocalMusicManager.obtenerPlaylistsPersonalizadas(this).toMutableList()

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("📂 Añadir a Playlist")

        val items = mutableListOf<String>()
        items.add("➕ Crear nueva playlist...")
        playlists.forEach {
            items.add("${it.nombre} (${it.canciones.size} canciones)")
        }

        builder.setItems(items.toTypedArray()) { dialog, which ->
            if (which == 0) {
                mostrarDialogoCrearPlaylist(cancion) { nueva ->
                    Toast.makeText(this, "✓ Playlist '${nueva.nombre}' creada con esta canción 🎵", Toast.LENGTH_SHORT).show()
                }
            } else {
                val targetPlaylist = playlists[which - 1]
                val added = LocalMusicManager.agregarCancionAPlaylist(this, targetPlaylist.id, cancion)
                if (added) {
                    Toast.makeText(this, "✓ Canción añadida a '${targetPlaylist.nombre}' 🎵", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No se pudo añadir a la playlist", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun mostrarDialogoCrearPlaylist(cancionToAdd: Cancion? = null, onCreated: ((CustomPlaylist) -> Unit)? = null) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("➕ Nueva Playlist")

        val input = EditText(this)
        input.hint = "Ej. Mis Favoritas, Fiesta, Rock"
        input.setPadding(36, 24, 36, 24)
        input.isSingleLine = true
        builder.setView(input)

        builder.setPositiveButton("Crear") { dialog, _ ->
            val nombre = input.text.toString().trim()
            if (nombre.isNotEmpty()) {
                val nueva = LocalMusicManager.crearPlaylistPersonalizada(this, nombre)
                if (cancionToAdd != null) {
                    LocalMusicManager.agregarCancionAPlaylist(this, nueva.id, cancionToAdd)
                }
                onCreated?.invoke(nueva)
            } else {
                Toast.makeText(this, "Por favor ingresa un nombre para la playlist", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
    }
}
