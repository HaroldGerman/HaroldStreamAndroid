package com.german.haroldstream

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * External playback bridge for assistants such as Dronnk.
 * Accepts tushnh://play?q=<search> and immediately searches TushNH,
 * selects the first result, starts playback and opens the normal UI.
 */
class DronnkPlayActivity : AppCompatActivity() {

    companion object {
        private const val BASE_URL = "https://haroldstream.me/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(source: Intent) {
        val query = source.data?.getQueryParameter("q")
            ?: source.getStringExtra("query")
            ?: ""

        if (query.isBlank()) {
            openMainAndFinish()
            return
        }

        PlayerManager.defaultServerUrl = BASE_URL
        Toast.makeText(this, "Buscando $query en TushNH…", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val api = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)

                val response = api.buscarCancion(query)
                val first = response.canciones.firstOrNull()
                if (first == null) {
                    Toast.makeText(this@DronnkPlayActivity, "No encontré $query en TushNH", Toast.LENGTH_SHORT).show()
                    openMainAndFinish()
                    return@launch
                }

                PlayerManager.establecerListaReproduccion(response.canciones, 0)
                PlayerManager.reproducirCancionDirecto(this@DronnkPlayActivity, first)
                Toast.makeText(this@DronnkPlayActivity, "Reproduciendo ${first.titulo ?: query}", Toast.LENGTH_SHORT).show()
                openMainAndFinish()
            } catch (t: Throwable) {
                Toast.makeText(this@DronnkPlayActivity, "No pude reproducir $query: ${t.message ?: "error"}", Toast.LENGTH_LONG).show()
                openMainAndFinish()
            }
        }
    }

    private fun openMainAndFinish() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        finish()
    }
}
