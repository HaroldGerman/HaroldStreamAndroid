package com.german.haroldstream

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class YouTubeFallbackActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_YOUTUBE_URL = "youtube_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_THUMBNAIL = "thumbnail"
    }

    private lateinit var webView: WebView
    private lateinit var header: LinearLayout
    private var youtubeUrl: String = ""
    private var videoId: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        youtubeUrl = intent.getStringExtra(EXTRA_YOUTUBE_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
        videoId = extractVideoId(youtubeUrl)

        if (youtubeUrl.isBlank() || videoId == null) {
            Toast.makeText(this, "No se pudo abrir esta canción dentro de TushNH.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 10, 14))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 18, 16, 14)
        }

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(this).apply {
            text = title.ifBlank { "Reproduciendo en TushNH" }
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
        }

        val tvArtist = TextView(this).apply {
            text = artist
            setTextColor(Color.LTGRAY)
            textSize = 14f
            visibility = if (artist.isBlank()) View.GONE else View.VISIBLE
        }

        val tvInfo = TextView(this).apply {
            text = "Reproductor interno de TushNH"
            setTextColor(Color.GRAY)
            textSize = 12f
            setPadding(0, 6, 0, 0)
        }

        textBox.addView(tvTitle)
        textBox.addView(tvArtist)
        textBox.addView(tvInfo)

        val btnMinimize = ImageButton(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            contentDescription = "Minimizar reproductor"
            setImageResource(android.R.drawable.arrow_down_float)
            setPadding(18, 18, 18, 18)
            setOnClickListener { minimizeToPip() }
        }

        val btnClose = ImageButton(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            contentDescription = "Cerrar reproductor"
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setPadding(18, 18, 18, 18)
            setOnClickListener { finish() }
        }

        header.addView(textBox)
        header.addView(btnMinimize)
        header.addView(btnClose)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"

            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url?.toString().orEmpty()

                    // Nunca sacar al usuario de TushNH. Navegación web externa se bloquea.
                    if (target.startsWith("intent:") ||
                        target.startsWith("vnd.youtube:") ||
                        target.contains("youtube.com/watch") ||
                        target.contains("youtu.be/")) {
                        return true
                    }
                    return false
                }
            }
        }

        root.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
        loadInternalPlayer(videoId!!)
    }

    private fun loadInternalPlayer(videoId: String) {
        val embedUrl =
            "https://www.youtube.com/embed/$videoId" +
            "?autoplay=1&playsinline=1&rel=0&modestbranding=1" +
            "&origin=https%3A%2F%2Fharoldstream.me"

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                <style>
                    html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden;}
                    iframe{position:absolute;inset:0;width:100%;height:100%;border:0;background:#000;}
                </style>
            </head>
            <body>
                <iframe
                    src="$embedUrl"
                    title="TushNH Player"
                    allow="autoplay; encrypted-media; picture-in-picture"
                    allowfullscreen>
                </iframe>
            </body>
            </html>
        """.trimIndent()

        // Base URL real para que WebView envíe identidad/origen válido a YouTube.
        webView.loadDataWithBaseURL(
            "https://haroldstream.me/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun minimizeToPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
                Toast.makeText(this, "No se pudo minimizar el reproductor.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Tu versión de Android no admite ventana flotante.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        header.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE

        if (isInPictureInPictureMode) {
            // Deja TushNH disponible detrás del reproductor flotante.
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(mainIntent)
        }
    }

    private fun extractVideoId(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            when {
                uri.host?.contains("youtu.be") == true -> uri.lastPathSegment
                uri.host?.contains("youtube.com") == true -> uri.getQueryParameter("v")
                else -> null
            }?.takeIf { it.length >= 6 }
        } catch (_: Exception) {
            null
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
        super.onDestroy()
    }
}
