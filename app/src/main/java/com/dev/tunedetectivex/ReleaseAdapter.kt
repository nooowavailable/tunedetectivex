package com.dev.tunedetectivex

import android.graphics.drawable.Drawable
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
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import java.text.SimpleDateFormat
import java.util.Locale

@Entity(tableName = "release_item")
data class ReleaseItem(
    @PrimaryKey val id: Long,
    val title: String,
    val artistName: String,
    val albumArtUrl: String,
    val releaseDate: String,
    val apiSource: String,
    val isHeader: Boolean = false,
    val deezerId: Long? = null,
    val itunesId: Long? = null
)

class ReleaseAdapter(
    private val onReleaseClick: (ReleaseItem) -> Unit
) : ListAdapter<ReleaseItem, RecyclerView.ViewHolder>(ReleaseDiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isHeader) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_release_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_release, parent, false)
            ReleaseViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is HeaderViewHolder) {
            holder.bind(item)
        } else if (holder is ReleaseViewHolder) {
            holder.bind(item)
            holder.itemView.setOnClickListener {
                onReleaseClick(item)
            }
        }
    }

    class ReleaseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val albumArt: ImageView = view.findViewById(R.id.imageViewAlbumArt)
        private val title: TextView = view.findViewById(R.id.textViewTitle)
        private val artistName: TextView = view.findViewById(R.id.textViewArtistName)
        private val releaseDate: TextView = view.findViewById(R.id.textViewReleaseDate)

        fun bind(item: ReleaseItem) {
            title.text = item.title
            artistName.text = item.artistName
            releaseDate.text = formatReleaseDate(item.releaseDate)

            Log.d(
                "ReleaseAdapter",
                "Binding release: ID=${item.id}, Title=${item.title}, Artist=${item.artistName}, Date=${item.releaseDate}"
            )

            val progressBar: ProgressBar = itemView.findViewById(R.id.progressBarLoading)

            progressBar.visibility = View.VISIBLE

            Glide.with(itemView.context)
                .load(item.albumArtUrl)
                .placeholder(R.drawable.ic_discography)
                .error(R.drawable.ic_discography)
                .transform(RoundedCorners(30))
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        albumArt.setImageDrawable(resource)
                        progressBar.visibility = View.GONE
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }
                })
        }



        private fun formatReleaseDate(date: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val parsedDate = inputFormat.parse(date)
                outputFormat.format(parsedDate ?: date)
            } catch (e: Exception) {
                Log.e("ReleaseAdapter", "Error formatting the date: ${e.message}")
                "Unknown Date"
            }
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val headerText: TextView = view.findViewById(R.id.textViewHeader)

        fun bind(item: ReleaseItem) {
            headerText.text = item.title.uppercase(Locale.getDefault())
        }
    }

    class ReleaseDiffCallback : DiffUtil.ItemCallback<ReleaseItem>() {
        override fun areItemsTheSame(oldItem: ReleaseItem, newItem: ReleaseItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ReleaseItem, newItem: ReleaseItem): Boolean {
            return oldItem == newItem
        }
    }
}