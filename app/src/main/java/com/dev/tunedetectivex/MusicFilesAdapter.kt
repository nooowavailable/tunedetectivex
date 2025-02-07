package com.dev.tunedetectivex

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MusicFilesAdapter(private var musicFiles: List<MusicFile>) :
    RecyclerView.Adapter<MusicFilesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewFileName: TextView = view.findViewById(R.id.textViewFileName)
        val textViewStatus: TextView = view.findViewById(R.id.textViewStatus)
        val imageViewCover: ImageView = view.findViewById(R.id.imageViewCover)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val musicFile = musicFiles[position]

        holder.textViewFileName.text = musicFile.fileName
        holder.textViewStatus.text = musicFile.status

        Glide.with(holder.itemView.context)
            .load(musicFile.coverUrl)
            .placeholder(R.drawable.error_image)
            .into(holder.imageViewCover)
    }

    override fun getItemCount() = musicFiles.size
}
