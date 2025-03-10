package com.dev.tunedetectivex

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DiscographyAdapter(
    private val albums: List<DeezerAlbum>,
    private val onAlbumClick: (DeezerAlbum) -> Unit
) : RecyclerView.Adapter<DiscographyAdapter.AlbumViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = albums[position]
        holder.bind(album)
        holder.itemView.setOnClickListener { onAlbumClick(album) }
    }

    override fun getItemCount(): Int = albums.size

    class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val albumTitle: TextView = view.findViewById(R.id.textViewAlbumTitle)
        private val albumCover: ImageView = view.findViewById(R.id.imageViewAlbumCover)
        private val releaseDate: TextView = view.findViewById(R.id.textViewReleaseDate)

        fun bind(album: DeezerAlbum) {
            albumTitle.text = album.title
            releaseDate.text = album.release_date

            val placeholderResId = R.drawable.ic_discography

            BitmapUtils.loadBitmapFromUrl(
                itemView.context as Activity,
                album.getBestCoverUrl(),
                albumCover,
                cornerRadius = 30f,
                isCircular = false,
                placeholderResId = placeholderResId
            )
        }
    }
}