package com.dev.tunedetectivex

import android.annotation.SuppressLint
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
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton

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
    val deezerId: Long? = null,
    val itunesId: Long? = null,
    var notifyOnNewRelease: Boolean = true
)

class SavedArtistAdapter(
    private val onDelete: (SavedArtistItem) -> Unit,
    private val onArtistClick: (SavedArtistItem) -> Unit,
    private val onToggleNotifications: (SavedArtistItem, Boolean) -> Unit
) : ListAdapter<SavedArtistItem, SavedArtistAdapter.SavedArtistViewHolder>(SavedArtistDiffCallback()) {

    private val selectedItems = mutableSetOf<Long>()
    var selectionMode = false
    var onSelectionChanged: ((List<SavedArtistItem>) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedArtistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.saved_artist_item, parent, false)
        return SavedArtistViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: SavedArtistViewHolder, position: Int) {
        val artist = getItem(position)
        val isSelected = selectedItems.contains(artist.id)

        holder.bind(
            artist = artist,
            onArtistClick = onArtistClick,
            onToggleNotifications = onToggleNotifications,
            isSelected = isSelected,
            selectionMode = selectionMode
        )

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelection(artist)
            } else {
                onArtistClick(artist)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!selectionMode) selectionMode = true
            toggleSelection(artist)
            true
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun toggleSelection(artist: SavedArtistItem) {
        val index = currentList.indexOfFirst { it.id == artist.id }
        if (index != -1) {
            if (selectedItems.contains(artist.id)) {
                selectedItems.remove(artist.id)
            } else {
                selectedItems.add(artist.id)
            }
            notifyItemChanged(index)
            onSelectionChanged?.invoke(currentList.filter { selectedItems.contains(it.id) })
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        selectedItems.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged?.invoke(emptyList())
    }

    fun isInSelectionMode(): Boolean = selectionMode



    override fun onViewRecycled(holder: SavedArtistViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView.context)
            .clear(holder.itemView.findViewById<ImageView>(R.id.imageViewProfile))
        holder.itemView.findViewById<ImageView>(R.id.imageViewProfile).setImageDrawable(null)
    }

    fun deleteItem(position: Int) {
        val artistToDelete = currentList[position]
        onDelete(artistToDelete)

        val updatedList = currentList.toMutableList().apply {
            removeAt(position)
        }
        submitList(updatedList.toList())
    }


    class SavedArtistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val artistNameTextView: TextView = view.findViewById(R.id.textViewArtistName)
        private val profileImageView: ImageView = view.findViewById(R.id.imageViewProfile)
        private val progressBar: ProgressBar = view.findViewById(R.id.progressBarItemLoading)
        private val notifyIcon: FloatingActionButton = view.findViewById(R.id.buttonNotifyToggle)

        @SuppressLint("SetTextI18n")
        fun bind(
            artist: SavedArtistItem,
            onArtistClick: (SavedArtistItem) -> Unit,
            onToggleNotifications: (SavedArtistItem, Boolean) -> Unit,
            isSelected: Boolean,
            selectionMode: Boolean
        ) {
            artistNameTextView.text = artist.name

            val context = profileImageView.context
            val glide = Glide.with(context)

            glide.clear(profileImageView)
            profileImageView.setImageDrawable(null)
            progressBar.visibility = View.VISIBLE

            val profileImageUrl = listOfNotNull(
                artist.picture_xl,
                artist.picture_big,
                artist.picture_medium,
                artist.picture_small,
                artist.picture
            ).firstOrNull()

            if (profileImageUrl != null) {
                glide
                    .load(profileImageUrl)
                    .dontAnimate()
                    .circleCrop()
                    .listener(object :
                        com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(
                            e: com.bumptech.glide.load.engine.GlideException?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            progressBar.visibility = View.GONE
                            return false
                        }

                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                            dataSource: com.bumptech.glide.load.DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            progressBar.visibility = View.GONE
                            return false
                        }
                    })
                    .into(profileImageView)
            } else {
                progressBar.visibility = View.GONE
                profileImageView.setImageResource(R.drawable.error_image)
            }

            notifyIcon.setImageResource(
                if (artist.notifyOnNewRelease) R.drawable.ic_saved_artist else R.drawable.ic_save_artist
            )

            notifyIcon.apply {
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = true
                isLongClickable = false
                isEnabled = !selectionMode
                visibility = if (selectionMode) View.INVISIBLE else View.VISIBLE

                setOnClickListener {
                    if (selectionMode) return@setOnClickListener

                    val newValue = !artist.notifyOnNewRelease
                    artist.notifyOnNewRelease = newValue
                    onToggleNotifications(artist, newValue)
                    setImageResource(
                        if (newValue) R.drawable.ic_saved_artist else R.drawable.ic_save_artist
                    )
                }
            }

            val selectedColor = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSurfaceVariant,
                "fallback"
            )
            val normalColor = android.graphics.Color.TRANSPARENT
            itemView.setBackgroundColor(if (isSelected) selectedColor else normalColor)

            notifyIcon.isEnabled = !selectionMode
            notifyIcon.visibility = if (selectionMode) View.INVISIBLE else View.VISIBLE

        }
    }

    class SavedArtistDiffCallback : DiffUtil.ItemCallback<SavedArtistItem>() {
        override fun areItemsTheSame(oldItem: SavedArtistItem, newItem: SavedArtistItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: SavedArtistItem,
            newItem: SavedArtistItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}
