package com.dev.tunedetectivex

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SimilarArtistsAdapter(
    private val context: Context,
    private val artists: List<DeezerArtist>,
    private val itemClick: (DeezerArtist) -> Unit
) : RecyclerView.Adapter<SimilarArtistsAdapter.ArtistViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.artist_item, parent, false)
        return ArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        val artist = artists[position]

        holder.nameTextView.text = artist.name

        val artistPictureUrl = getBestArtistPicture(artist)
        if (artistPictureUrl.isNotEmpty()) {
            BitmapUtils.loadBitmapFromUrl(
                context as Activity,
                artistPictureUrl,
                holder.artistImageView,
                isCircular = true
            )
        } else {
            holder.artistImageView.setImageResource(R.drawable.placeholder_image)
        }

        holder.itemView.setOnClickListener { itemClick(artist) }
    }

    override fun getItemCount(): Int = artists.size

    private fun getBestArtistPicture(artist: DeezerArtist): String {
        return when {
            !artist.picture_xl.isNullOrEmpty() -> artist.picture_xl
            !artist.picture_big.isNullOrEmpty() -> artist.picture_big
            !artist.picture_medium.isNullOrEmpty() -> artist.picture_medium
            !artist.picture_small.isNullOrEmpty() -> artist.picture_small
            else -> ""
        }
    }

    class ArtistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.name)
        val artistImageView: ImageView = view.findViewById(R.id.artistImage)
    }
}
