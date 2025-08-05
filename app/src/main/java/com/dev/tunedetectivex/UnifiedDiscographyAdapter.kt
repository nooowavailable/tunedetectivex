package com.dev.tunedetectivex

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import java.text.SimpleDateFormat
import java.util.Locale

class UnifiedDiscographyAdapter(
    val albums: List<UnifiedAlbum>,
    private val isListLayout: Boolean,
    private val onAlbumClick: (UnifiedAlbum) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_LIST = 1
    private val VIEW_TYPE_GRID = 2

    init {
        val deezerCount = albums.count { it.deezerId != null }
        val itunesCount = albums.count { it.itunesId != null }
        val bothCount = albums.count { it.deezerId != null && it.itunesId != null }
        val total = albums.size

        Log.d("UnifiedDiscography", "▶ Total: $total Releases")
        Log.d("UnifiedDiscography", "• Deezer: $deezerCount")
        Log.d("UnifiedDiscography", "• iTunes: $itunesCount")
        Log.d("UnifiedDiscography", "• Both IDs present: $bothCount")
    }

    override fun getItemViewType(position: Int): Int {
        return if (isListLayout) VIEW_TYPE_LIST else VIEW_TYPE_GRID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_LIST -> {
                val view = inflater.inflate(R.layout.item_album, parent, false)
                ListViewHolder(view)
            }
            VIEW_TYPE_GRID -> {
                val view = inflater.inflate(R.layout.item_release_grid, parent, false)
                GridViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val album = albums[position]
        when (holder) {
            is ListViewHolder -> holder.bind(album, onAlbumClick)
            is GridViewHolder -> holder.bind(album, onAlbumClick)
        }
    }

    override fun getItemCount(): Int = albums.size

    class ListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val albumCover: ImageView = view.findViewById(R.id.imageViewAlbumCover)
        private val releaseDate: TextView = view.findViewById(R.id.textViewReleaseDate)
        private val progressBar: ProgressBar = view.findViewById(R.id.progressBarLoading)
        private val titleMain: TextView = itemView.findViewById(R.id.textViewTitleMain)
        private val titleType: TextView = itemView.findViewById(R.id.textViewTitleType)

        fun bind(album: UnifiedAlbum, onAlbumClick: (UnifiedAlbum) -> Unit) {
            Log.d("UnifiedDiscography", "Album: ${album.title}")
            Log.d("UnifiedDiscography", "  Deezer ID: ${album.deezerId ?: "❌"}")
            Log.d("UnifiedDiscography", "  iTunes ID: ${album.itunesId ?: "❌"}")

            val rawTitle = album.title
            val parenMatch =
                Regex("\\((Single|EP|Album)\\)", RegexOption.IGNORE_CASE).find(rawTitle)
            val dashMatch =
                Regex("-(\\s*)(Single|EP|Album)", RegexOption.IGNORE_CASE).find(rawTitle)

            val extractedType = when {
                parenMatch != null -> parenMatch.groupValues[1]
                dashMatch != null -> dashMatch.groupValues[2]
                else -> null
            }?.replaceFirstChar { it.uppercaseChar() }

            val finalType = album.releaseType ?: extractedType

            val cleanedTitle = rawTitle
                .replace(Regex("\\s*-\\s*(Single|EP|Album)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\((Single|EP|Album)\\)", RegexOption.IGNORE_CASE), "")
                .trim()

            titleMain.text = cleanedTitle
            if (!finalType.isNullOrBlank()) {
                titleType.text = "($finalType)"
                titleType.visibility = View.VISIBLE
            } else {
                titleType.text = ""
                titleType.visibility = View.GONE
            }

            val formattedDate = try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val date = inputFormat.parse(album.releaseDate)
                outputFormat.format(date ?: album.releaseDate)
            } catch (e: Exception) {
                "Unknown Date"
            }
            releaseDate.text = formattedDate

            progressBar.visibility = View.VISIBLE
            Glide.with(itemView.context).clear(albumCover)
            albumCover.setImageDrawable(null)

            Glide.with(itemView.context)
                .load(album.coverUrl)
                .error(R.drawable.ic_discography)
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                .transform(RoundedCorners(30))
                .listener(object : com.bumptech.glide.request.RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBar.visibility = View.GONE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: com.bumptech.glide.request.target.Target<Drawable>,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBar.visibility = View.GONE
                        return false
                    }
                })
                .into(albumCover)

            itemView.setOnClickListener { onAlbumClick(album) }
        }
    }

    class GridViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val albumArt: ImageView = view.findViewById(R.id.imageViewAlbumArt)
        private val artistName: TextView = view.findViewById(R.id.textViewArtistName)
        private val titleMain: TextView = view.findViewById(R.id.textViewTitleMain)

        fun bind(album: UnifiedAlbum, onAlbumClick: (UnifiedAlbum) -> Unit) {
            val rawTitle = album.title
            val cleanedTitle = rawTitle
                .replace(Regex("\\s*-\\s*(Single|EP|Album)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\((Single|EP|Album)\\)", RegexOption.IGNORE_CASE), "")
                .trim()

            titleMain.text = cleanedTitle
            artistName.text = album.artistName

            Glide.with(itemView.context).clear(albumArt)

            if (!album.coverUrl.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(album.coverUrl)
                    .error(R.drawable.ic_discography)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                    .transform(RoundedCorners(30))
                    .into(albumArt)
            } else {
                albumArt.setImageResource(R.drawable.ic_discography)
            }

            itemView.setOnClickListener {
                onAlbumClick(album)
            }
        }
    }
}