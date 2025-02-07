package com.dev.tunedetectivex

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TrackAdapter : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    private val tracks = mutableListOf<Track>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(trackList: List<Track>) {
        tracks.clear()
        tracks.addAll(trackList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        holder.bind(track, position)
    }

    override fun getItemCount(): Int = tracks.size

    inner class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val trackNumber: TextView = itemView.findViewById(R.id.textViewTrackNumber)
        private val trackTitle: TextView = itemView.findViewById(R.id.textViewTrackTitle)
        private val trackDuration: TextView = itemView.findViewById(R.id.textViewTrackDuration)

        @SuppressLint("SetTextI18n")
        fun bind(track: Track, position: Int) {
            trackNumber.text = (position + 1).toString()
            trackTitle.text = track.title
            trackDuration.text = formatDuration(track.duration)
        }

        @SuppressLint("DefaultLocale")
        private fun formatDuration(duration: Int): String {
            val minutes = duration / 60
            val seconds = duration % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    }
}

