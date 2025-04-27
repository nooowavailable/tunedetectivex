package com.dev.tunedetectivex

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dev.tunedetectivex.models.ItunesArtistDisplayItem

class ItunesArtistResultAdapter(
    private val onItemClick: (ItunesArtistDisplayItem) -> Unit
) : RecyclerView.Adapter<ItunesArtistResultAdapter.ViewHolder>() {

    private var items: List<ItunesArtistDisplayItem> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<ItunesArtistDisplayItem>) {
        items = newList
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageCover: ImageView = view.findViewById(R.id.imageCover)
        val textName: TextView = view.findViewById(R.id.textArtistName)
        val textRelease: TextView = view.findViewById(R.id.textLatestRelease)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_itunes_artist_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.textName.text = item.artist.artistName
        val context = holder.itemView.context
        holder.textRelease.text = item.latestTitle?.let {
            context.getString(R.string.latest_release_info, it, item.latestDate ?: "?")
        } ?: context.getString(R.string.no_release_found)

        Glide.with(holder.itemView)
            .load(item.artist.getHighResArtwork())
            .into(holder.imageCover)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }


    override fun getItemCount(): Int = items.size
}
