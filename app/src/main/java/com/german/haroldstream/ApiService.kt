package com.german.haroldstream

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Headers
import retrofit2.http.DELETE
import retrofit2.http.Path

interface ApiService {

    @Headers("ngrok-skip-browser-warning: any_value")
    @GET("descargar")
    suspend fun descargarCancion(@Query("url") youtubeUrl: String): ResponseData

    @Headers("ngrok-skip-browser-warning: any_value")
    @GET("buscar")
    suspend fun buscarCancion(@Query("termino") termino: String): SearchResponse

    @Headers("ngrok-skip-browser-warning: any_value")
    @GET("canciones")
    suspend fun obtenerCancionesDescargadas(): SearchResponse

    @Headers("ngrok-skip-browser-warning: any_value")
    @DELETE("canciones/{archivo}")
    suspend fun eliminarCancion(@Path("archivo") archivo: String): DeleteResponse

    @Headers("ngrok-skip-browser-warning: any_value")
    @GET("api/version")
    suspend fun obtenerVersion(): VersionResponse

    @Headers("ngrok-skip-browser-warning: any_value")
    @GET("api/suggest")
    suspend fun obtenerSugerencias(@Query("q") query: String): SuggestResponse

    @Headers("ngrok-skip-browser-warning: any_value")
    @GET("api/buscar-playlists")
    suspend fun buscarPlaylists(@Query("termino") termino: String): PlaylistSearchResponse

    @Headers("ngrok-skip-browser-warning: any_value")
    @GET("api/playlist-songs")
    suspend fun obtenerCancionesPlaylist(@Query("url") url: String): SearchResponse
}

// Añade este data class al final de tu archivo ApiService.kt
data class SuggestResponse(val suggestions: List<String>?)


data class ResponseData(
    val status: String?,
    val url: String?,
    val titulo: String?,
    val archivo: String?,
    val thumbnail: String?,
    val canal: String?,
    val duracion: String?,
    val message: String?
)

data class SearchResponse(val canciones: List<Cancion>)

data class DeleteResponse(val status: String?, val message: String?)

data class Cancion(
    val id: String? = null,
    val titulo: String? = null,
    val url: String? = null,
    val thumbnail: String? = null,
    val duracion: String? = null,
    val canal: String? = null,
    val archivo: String? = null,
    var isFavorite: Boolean = false,
    var isDownloaded: Boolean = false,
    var localPath: String? = null
)

data class VersionResponse(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val minVersionCode: Int
)

data class PlaylistSearchResponse(val playlists: List<Playlist>)

data class Playlist(
    val id: String? = null,
    val titulo: String? = null,
    val url: String? = null,
    val thumbnail: String? = null,
    val video_count: Int = 0,
    val canal: String? = null
)
