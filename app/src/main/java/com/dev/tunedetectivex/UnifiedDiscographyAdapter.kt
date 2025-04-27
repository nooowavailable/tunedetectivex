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
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
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
        private val albumTitle: TextView = view.findViewById(R.id.textViewAlbumTitle)
        private val albumCover: ImageView = view.findViewById(R.id.imageViewAlbumCover)
        private val releaseDate: TextView = view.findViewById(R.id.textViewReleaseDate)
        private val progressBar: ProgressBar = view.findViewById(R.id.progressBarLoading)

        fun bind(album: UnifiedAlbum) {
            Log.d("UnifiedDiscography", "Album: ${album.title}")
            Log.d("UnifiedDiscography", "  Deezer ID: ${album.deezerId ?: "❌"}")
            Log.d("UnifiedDiscography", "  iTunes ID: ${album.itunesId ?: "❌"}")

            albumTitle.text = buildString {
                append(album.title)
                album.releaseType?.let {
                    append(" ($it)")
                }
            }

            val formattedDate = try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val date = inputFormat.parse(album.releaseDate)
                outputFormat.format(date!!)
            } catch (e: Exception) {
                "Unknown Date"
            }
            releaseDate.text = formattedDate

            progressBar.visibility = View.VISIBLE

            Glide.with(itemView.context)
                .load(album.coverUrl)
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
