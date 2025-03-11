package com.dev.tunedetectivex

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
        val placeholderResId = R.drawable.placeholder_image

        if (artistPictureUrl.isNotEmpty()) {
            BitmapUtils.loadBitmapFromUrl(
                context as Activity,
                artistPictureUrl,
                holder.artistImageView,
                isCircular = true,
                placeholderResId = placeholderResId
            )
        } else {
            holder.artistImageView.setImageResource(placeholderResId)
        }

        holder.itemView.setOnClickListener {
            checkNetworkTypeAndSetFlag()
            itemClick(artist)
        }
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

    private fun checkNetworkTypeAndSetFlag() {
        val sharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"
        val isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(context, networkType)

        if (!isNetworkRequestsAllowed) {
            Toast.makeText(
                context,
                context.getString(R.string.network_type_not_available),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    class ArtistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.name)
        val artistImageView: ImageView = view.findViewById(R.id.artistImage)
    }
}