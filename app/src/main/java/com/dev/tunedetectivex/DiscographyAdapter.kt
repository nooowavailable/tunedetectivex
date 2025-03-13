package com.dev.tunedetectivex

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

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

            val progressBar: ProgressBar = itemView.findViewById(R.id.progressBarLoading)

            progressBar.visibility = View.VISIBLE

            Glide.with(itemView.context)
                .load(album.getBestCoverUrl())
                .placeholder(R.drawable.ic_discography)
                .error(R.drawable.ic_discography)
                .transform(RoundedCorners(30))
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        albumCover.setImageDrawable(resource)

                        progressBar.visibility = View.GONE
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        progressBar.visibility = View.GONE
                    }
                })
        }
    }
}