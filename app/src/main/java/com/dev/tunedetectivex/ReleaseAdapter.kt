package com.dev.tunedetectivex

import android.annotation.SuppressLint
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
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
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
    val itunesId: Long? = null,
    val artistImageUrl: String? = null,
    val releaseType: String? = null
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
        private val artistThumb: ImageView? = view.findViewById(R.id.imageViewArtistThumb)
        private val artistName: TextView = view.findViewById(R.id.textViewArtistName)
        private val releaseMeta: TextView = view.findViewById(R.id.textViewReleaseMeta)

        @SuppressLint("SetTextI18n")
        fun bind(item: ReleaseItem) {
            val titleMain: TextView = itemView.findViewById(R.id.textViewTitleMain)
            val titleType: TextView = itemView.findViewById(R.id.textViewTitleType)
            val progressBar: ProgressBar = itemView.findViewById(R.id.progressBarLoading)

            val rawTitle = item.title

            val parenMatch =
                Regex("\\((Single|EP|Album)\\)", RegexOption.IGNORE_CASE).find(rawTitle)
            val dashMatch =
                Regex("-(\\s*)(Single|EP|Album)", RegexOption.IGNORE_CASE).find(rawTitle)

            val type = when {
                parenMatch != null -> parenMatch.groupValues[1]
                dashMatch != null -> dashMatch.groupValues[2]
                else -> null
            }?.replaceFirstChar { it.uppercaseChar() }
            val cleanedTitle = rawTitle
                .replace(Regex("\\s*-\\s*(Single|EP|Album)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\((Single|EP|Album)\\)", RegexOption.IGNORE_CASE), "")
                .trim()

            titleMain.text = cleanedTitle
            if (type != null) {
                titleType.text = "($type)"
                titleType.visibility = View.VISIBLE
            } else {
                titleType.text = ""
                titleType.visibility = View.GONE
            }

            progressBar.visibility = View.VISIBLE
            Glide.with(itemView.context).clear(albumArt)

            if (!item.albumArtUrl.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(item.albumArtUrl)
                    .error(R.drawable.error_image)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .transform(RoundedCorners(50))
                    .listener(object : com.bumptech.glide.request.RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<Drawable>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            progressBar.visibility = View.GONE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<Drawable>?,
                            dataSource: com.bumptech.glide.load.DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            progressBar.visibility = View.GONE
                            return false
                        }
                    })
                    .into(albumArt)
            } else {
                albumArt.setImageResource(R.drawable.placeholder_image)
                progressBar.visibility = View.GONE
            }

            artistThumb?.let {
                if (!item.artistImageUrl.isNullOrBlank()) {
                    Glide.with(itemView.context)
                        .load(item.artistImageUrl)
                        .placeholder(R.drawable.placeholder_image)
                        .circleCrop()
                        .into(it)
                } else {
                    it.setImageResource(R.drawable.placeholder_image)
                }
            }

            artistName.text = item.artistName
            releaseMeta.text = formatReleaseDate(item.releaseDate)
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