package com.dev.tunedetectivex

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

data class SavedArtistItem(
    val id: Long,
    val name: String = "Unknown Artist",
    val lastReleaseTitle: String? = null,
    val lastReleaseDate: String? = null,
    val picture: String? = null,
    val picture_small: String? = null,
    val picture_medium: String? = null,
    val picture_big: String? = null,
    val picture_xl: String? = null,
    var isLoading: Boolean = false
)

class SavedArtistAdapter(
    private val onDelete: (SavedArtistItem) -> Unit
) : ListAdapter<SavedArtistItem, SavedArtistAdapter.SavedArtistViewHolder>(SavedArtistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedArtistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.saved_artist_item, parent, false)
        return SavedArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: SavedArtistViewHolder, position: Int) {
        val artist = getItem(position)
        holder.bind(artist)
    }

    fun deleteItem(position: Int) {
        val artistToDelete = currentList[position]
        onDelete(artistToDelete)
        val updatedList = currentList.toMutableList()
        updatedList.removeAt(position)
        submitList(updatedList)
    }


    class SavedArtistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val artistNameTextView: TextView = view.findViewById(R.id.textViewArtistName)
        private val profileImageView: ImageView = view.findViewById(R.id.imageViewProfile)
        private val progressBar: ProgressBar = view.findViewById(R.id.progressBarItemLoading)
        private val loadingStatusTextView: TextView = view.findViewById(R.id.textViewLoadingStatus)

        fun bind(artist: SavedArtistItem) {
            artistNameTextView.text = artist.name

            val profileImageUrl = artist.picture_xl
                ?: artist.picture_big
                ?: artist.picture_medium
                ?: artist.picture_small
                ?: artist.picture

            Glide.with(itemView.context)
                .load(profileImageUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .into(profileImageView)

            if (artist.isLoading) {
                progressBar.visibility = View.VISIBLE
                loadingStatusTextView.visibility = View.VISIBLE
                loadingStatusTextView.text = "Loading details..."
            } else {
                progressBar.visibility = View.GONE
                loadingStatusTextView.visibility = View.GONE
            }
        }
    }
}

class SavedArtistDiffCallback : DiffUtil.ItemCallback<SavedArtistItem>() {
    override fun areItemsTheSame(oldItem: SavedArtistItem, newItem: SavedArtistItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: SavedArtistItem, newItem: SavedArtistItem): Boolean {
        return oldItem == newItem
    }
}
