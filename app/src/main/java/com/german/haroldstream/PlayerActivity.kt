package com.german.haroldstream

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import android.content.Intent
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity(), PlayerManager.PlayerStateListener {

    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPlayerStar: ImageButton
    private lateinit var btnDownloadMobile: ImageButton

    private var currentCancionLocal: Cancion? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    companion object {
        const val EXTRA_STREAM_URL = "extra_stream_url"
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

        val btnRewind = findViewById<ImageButton>(R.id.btn_rewind)
        val btnForward = findViewById<ImageButton>(R.id.btn_forward)
        btnDownloadMobile = findViewById<ImageButton>(R.id.btn_download_mobile)

        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "HaroldSound Audio"
        val thumbnail = intent.getStringExtra(EXTRA_THUMBNAIL)
        val canal = intent.getStringExtra(EXTRA_CANAL) ?: "Desconocido"

        currentCancionLocal = Cancion(titulo = title, thumbnail = thumbnail, canal = canal, url = streamUrl)

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

        val btnShare = findViewById<ImageButton>(R.id.btn_share)
        btnShare?.setOnClickListener {
            currentCancionLocal?.let { cancion ->
                mostrarDialogoCompartir(cancion)
            }
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
            PlayerManager.siguienteCancion(this)
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

    private fun mostrarDialogoCompartir(cancion: Cancion) {
        val p = PlayerManager.player
        val currentSec = if (p != null) (p.currentPosition / 1000).toInt() else 0
        val defaultEnd = currentSec + 15

        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val tvInfo = TextView(context).apply {
            text = "Selecciona el fragmento a recortar (en segundos):"
            textSize = 14f
            setPadding(0, 0, 0, 20)
        }
        layout.addView(tvInfo)

        val layoutTimes = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val etStart = EditText(context).apply {
            hint = "Inicio (seg)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentSec.toString())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val etEnd = EditText(context).apply {
            hint = "Fin (seg)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(defaultEnd.toString())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        layoutTimes.addView(etStart)
        layoutTimes.addView(etEnd)
        layout.addView(layoutTimes)

        val builder = AlertDialog.Builder(context)
        builder.setTitle("✂️ Recortar y Compartir Fragmento")
        builder.setView(layout)

        builder.setPositiveButton("🎵 Estado (Portada + Audio)") { _, _ ->
            val start = etStart.text.toString().toIntOrNull() ?: 0
            val end = etEnd.text.toString().toIntOrNull() ?: 15
            if (start >= 0 && end > start) {
                descargarYCompartirClip(cancion, start, end, isAudioOnly = true)
            } else {
                Toast.makeText(context, "Rango de tiempo inválido", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNeutralButton("🎥 Clip de Video") { _, _ ->
            val start = etStart.text.toString().toIntOrNull() ?: 0
            val end = etEnd.text.toString().toIntOrNull() ?: 15
            if (start >= 0 && end > start) {
                descargarYCompartirClip(cancion, start, end, isAudioOnly = false)
            } else {
                Toast.makeText(context, "Rango de tiempo inválido", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }

        val alert = builder.create()
        alert.show()
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

        val baseUrl = if (MainActivity.DEFAULT_URL.endsWith("/")) MainActivity.DEFAULT_URL else "${MainActivity.DEFAULT_URL}/"
        val endpoint = if (isAudioOnly) {
            "${baseUrl}api/recortar-audio-portada?url=${Uri.encode(originalUrl)}&thumb=${Uri.encode(cancion.thumbnail ?: "")}&start=$start&end=$end&title=${Uri.encode(cancion.titulo ?: "")}&artist=${Uri.encode(cancion.canal ?: "")}"
        } else {
            "${baseUrl}api/recortar-video?url=${Uri.encode(originalUrl)}&start=$start&end=$end"
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

            // Creamos el intent general sin forzar ninguna app específica
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Invocamos el menú de "Compartir" universal de Android
            val chooser = Intent.createChooser(shareIntent, "Compartir fragmento en...")
            startActivity(chooser)

        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir archivo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
    }
}
