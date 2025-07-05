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
import com.dev.tunedetectivex.models.UnifiedAlbum
import java.text.SimpleDateFormat
import java.util.Locale

class UnifiedDiscographyAdapter(
    private val albums: List<UnifiedAlbum>,
    private val onAlbumClick: (UnifiedAlbum) -> Unit
) : RecyclerView.Adapter<UnifiedDiscographyAdapter.AlbumViewHolder>() {

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
        private val albumCover: ImageView = view.findViewById(R.id.imageViewAlbumCover)
        private val releaseDate: TextView = view.findViewById(R.id.textViewReleaseDate)
        private val progressBar: ProgressBar = view.findViewById(R.id.progressBarLoading)

        fun bind(album: UnifiedAlbum) {
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

            val titleMain: TextView = itemView.findViewById(R.id.textViewTitleMain)
            val titleType: TextView = itemView.findViewById(R.id.textViewTitleType)

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
                .transform(RoundedCorners(50))
                .listener(object : com.bumptech.glide.request.RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
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
                .into(albumCover)
        }
    }
}
