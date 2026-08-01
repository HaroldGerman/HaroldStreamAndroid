package com.german.haroldstream

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class DashboardHorizontalAdapter(
    var canciones: List<Cancion>,
    private val showNewBadge: Boolean,
    private val onClick: (Cancion, Int) -> Unit
) : RecyclerView.Adapter<DashboardHorizontalAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.iv_dash_item_cover)
        val tvTitle: TextView = view.findViewById(R.id.tv_dash_item_title)
        val tvArtist: TextView = view.findViewById(R.id.tv_dash_item_artist)
        val tvBadge: TextView = view.findViewById(R.id.tv_dash_item_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_horizontal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cancion = canciones[position]
        holder.tvTitle.text = cancion.titulo ?: "Sin título"
        holder.tvArtist.text = if (cancion.canal == "<unknown>") "HaroldSound" else (cancion.canal ?: "HaroldSound")
        
        holder.ivCover.load(cancion.thumbnail) {
            crossfade(true)
            placeholder(android.R.drawable.ic_media_play)
            error(android.R.drawable.ic_media_play)
        }

        if (showNewBadge) {
            holder.tvBadge.visibility = View.VISIBLE
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onClick(cancion, position)
        }
    }

    override fun getItemCount(): Int = canciones.size

    fun actualizarLista(nuevas: List<Cancion>) {
        canciones = nuevas
        notifyDataSetChanged()
    }
}
