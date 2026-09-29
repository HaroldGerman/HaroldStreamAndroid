package com.german.haroldstream

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object UpdateManager {
    private const val TAG = "UpdateManager"

    /**
     * Comprueba si hay actualizaciones disponibles consultando la API del backend.
     * Si hay una nueva versión, muestra el diálogo de actualización.
     */
    suspend fun checkForUpdates(activity: AppCompatActivity, baseUrl: String) {
        val versionInfo = fetchVersionInfo(baseUrl) ?: return
        
        val serverVersionCode = versionInfo.optInt("versionCode", -1)
        val serverVersionName = versionInfo.optString("versionName", "1.0")
        val releaseNotes = versionInfo.optString("releaseNotes", "Nuevas mejoras disponibles.")

        val currentVersionCode = com.german.haroldstream.BuildConfig.VERSION_CODE

        Log.i(TAG, "Versión instalada: $currentVersionCode, Versión en servidor: $serverVersionCode")

        if (serverVersionCode > currentVersionCode) {
            withContext(Dispatchers.Main) {
                showUpdateDialog(activity, baseUrl, serverVersionName, releaseNotes)
            }
        }
    }

    /**
     * Consulta el endpoint /api/version de forma asíncrona.
     */
    private suspend fun fetchVersionInfo(baseUrl: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                val url = "${cleanBaseUrl}api/version"
                
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrEmpty()) {
                            JSONObject(bodyString)
                        } else null
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al consultar versión en el servidor: ${e.message}")
                null
            }
        }
    }

    /**
     * Muestra la alerta de actualización al usuario.
     */
    private fun showUpdateDialog(
        activity: AppCompatActivity,
        baseUrl: String,
        serverVersionName: String,
        releaseNotes: String
    ) {
        AlertDialog.Builder(activity)
            .setTitle("🚀 Nueva versión disponible (v$serverVersionName)")
            .setMessage("Notas de la versión:\n$releaseNotes\n\n¿Deseas descargar y actualizar la aplicación ahora?")
            .setCancelable(false)
            .setPositiveButton("Actualizar") { dialog, _ ->
                dialog.dismiss()
                downloadAndInstallApk(activity, baseUrl)
            }
            .setNegativeButton("Más tarde") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Descarga el APK desde /api/download-apk e inicia el proceso de instalación.
     */
    private fun downloadAndInstallApk(activity: AppCompatActivity, baseUrl: String) {
        val progressDialog = ProgressDialog(activity).apply {
            setTitle("Descargando actualización")
            setMessage("Descargando archivo APK...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val apkUrl = "${cleanBaseUrl}api/download-apk"

        activity.lifecycleScope.launch(Dispatchers.Main) {
            val apkFile = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient()
                    val request = Request.Builder().url(apkUrl).build()
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) throw Exception("Código de error del servidor: ${response.code}")

                    val body = response.body ?: throw Exception("Cuerpo de respuesta vacío")
                    val contentLength = body.contentLength()
                    
                    val file = File(activity.cacheDir, "TushNH_update.apk")
                    if (file.exists()) file.delete()

                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(file)
                    
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalBytesRead: Long = 0

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        if (contentLength > 0) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt()
                            withContext(Dispatchers.Main) {
                                progressDialog.progress = progress
                            }
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                    file
                } catch (e: Exception) {
                    Log.e(TAG, "Error al descargar APK: ${e.message}")
                    null
                }
            }

            progressDialog.dismiss()

            if (apkFile != null && apkFile.exists()) {
                installApk(activity, apkFile)
            } else {
                Toast.makeText(activity, "Error al descargar la actualización", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Lanza el Intent para instalar el archivo APK descargado.
     */
    private fun installApk(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar instalador de APK: ${e.message}")
            Toast.makeText(context, "No se pudo iniciar el instalador de la app", Toast.LENGTH_LONG).show()
        }
    }
}
