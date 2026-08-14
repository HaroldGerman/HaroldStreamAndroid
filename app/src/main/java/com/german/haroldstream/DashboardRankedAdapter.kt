package com.german.haroldstream

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class DashboardRankedAdapter(
    var canciones: List<Cancion>,
    private val onClick: (Cancion, Int) -> Unit,
    private val onOptionsClick: (Cancion) -> Unit
) : RecyclerView.Adapter<DashboardRankedAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tv_rank_number)
        val ivThumb: ImageView = view.findViewById(R.id.iv_rank_thumbnail)
        val tvTitle: TextView = view.findViewById(R.id.tv_rank_title)
        val tvArtist: TextView = view.findViewById(R.id.tv_rank_artist)
        val btnOptions: ImageButton = view.findViewById(R.id.btn_rank_options)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_ranked, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cancion = canciones[position]
        holder.tvRank.text = (position + 1).toString()
        holder.tvTitle.text = cancion.titulo ?: "Sin título"
        holder.tvArtist.text = if (cancion.canal == "<unknown>") "HaroldSound" else (cancion.canal ?: "HaroldSound")
        
        holder.ivThumb.load(cancion.thumbnail) {
            crossfade(true)
            placeholder(android.R.drawable.ic_media_play)
            error(android.R.drawable.ic_media_play)
        }

        holder.itemView.setOnClickListener {
            onClick(cancion, position)
        }

        holder.btnOptions.setOnClickListener {
            onOptionsClick(cancion)
        }
    }

    override fun getItemCount(): Int = canciones.size

    fun actualizarLista(nuevas: List<Cancion>) {
        canciones = nuevas
        notifyDataSetChanged()
    }
}
