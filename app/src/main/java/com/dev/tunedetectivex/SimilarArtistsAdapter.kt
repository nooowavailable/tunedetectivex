package com.dev.tunedetectivex

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

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
        val placeholderResId = R.drawable.placeholder_circle_shape
        val progressBar: ProgressBar = holder.itemView.findViewById(R.id.progressBarLoading)

        progressBar.visibility = View.VISIBLE

        if (artistPictureUrl.isNotEmpty()) {
            Glide.with(context)
                .load(artistPictureUrl)
                .placeholder(placeholderResId)
                .error(placeholderResId)
                .circleCrop()
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        holder.artistImageView.setImageDrawable(resource)
                        progressBar.visibility = View.GONE
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        progressBar.visibility = View.GONE
                    }
                })
        } else {
            holder.artistImageView.setImageResource(placeholderResId)
            progressBar.visibility = View.GONE
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
            !artist.picture.isNullOrEmpty() -> artist.picture
            else -> ""
        }
    }

    private fun checkNetworkTypeAndSetFlag(): Boolean {
        val networkPreference = WorkManagerUtil.getNetworkPreferenceFromPrefs(context)
        return WorkManagerUtil.isSelectedNetworkTypeAvailable(context, networkPreference)
    }

    class ArtistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.name)
        val artistImageView: ImageView = view.findViewById(R.id.artistImage)
    }
}
