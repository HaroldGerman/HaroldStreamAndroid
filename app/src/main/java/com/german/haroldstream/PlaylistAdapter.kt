package com.german.haroldstream

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class PlaylistAdapter(
    private var playlists: List<Playlist>,
    private val onClick: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumbnail: ImageView = view.findViewById(R.id.iv_playlist_thumbnail)
        val tvTitulo: TextView = view.findViewById(R.id.tv_playlist_titulo)
        val tvAutor: TextView = view.findViewById(R.id.tv_playlist_autor)
        val tvVideoCount: TextView = view.findViewById(R.id.tv_playlist_video_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.tvTitulo.text = playlist.titulo ?: "Sin título"
        holder.tvAutor.text = playlist.canal ?: "YouTube"
        holder.tvVideoCount.text = playlist.video_count.toString()

        if (!playlist.thumbnail.isNullOrEmpty()) {
            holder.ivThumbnail.load(playlist.thumbnail) {
                crossfade(true)
                placeholder(android.R.color.darker_gray)
                error(android.R.color.darker_gray)
            }
        } else {
            holder.ivThumbnail.setImageResource(android.R.color.darker_gray)
        }

        holder.itemView.setOnClickListener {
            onClick(playlist)
        }
    }

    override fun getItemCount(): Int = playlists.size

    fun actualizarLista(nuevaLista: List<Playlist>) {
        playlists = nuevaLista
        notifyDataSetChanged()
    }
}
