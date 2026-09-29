package com.german.haroldstream

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
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
    private var youtubeUrl: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        youtubeUrl = intent.getStringExtra(EXTRA_YOUTUBE_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()

        if (youtubeUrl.isBlank()) {
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

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 20)
        }

        val tvTitle = TextView(this).apply {
            text = title.ifBlank { "Reproduciendo en YouTube" }
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val tvArtist = TextView(this).apply {
            text = artist
            setTextColor(Color.LTGRAY)
            textSize = 14f
            visibility = if (artist.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        }

        val tvInfo = TextView(this).apply {
            text = "TushNH cambió al reproductor de YouTube porque el stream directo no estuvo disponible."
            setTextColor(Color.GRAY)
            textSize = 12f
            setPadding(0, 10, 0, 12)
        }

        val btnExternal = Button(this).apply {
            text = "Abrir en YouTube"
            setOnClickListener { openExternalYouTube() }
        }

        header.addView(tvTitle)
        header.addView(tvArtist)
        header.addView(tvInfo)
        header.addView(btnExternal)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url?.toString().orEmpty()
                    if (target.startsWith("intent:") || target.startsWith("vnd.youtube:")) {
                        openExternalYouTube()
                        return true
                    }
                    return false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        Toast.makeText(
                            this@YouTubeFallbackActivity,
                            "No se pudo cargar el reproductor interno. Abriendo YouTube...",
                            Toast.LENGTH_SHORT
                        ).show()
                        openExternalYouTube()
                    }
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

        val videoId = extractVideoId(youtubeUrl)
        if (videoId != null) {
            val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
            webView.loadUrl(embedUrl)
        } else {
            webView.loadUrl(youtubeUrl)
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

    private fun openExternalYouTube() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo abrir YouTube.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
