package com.dev.tunedetectivex

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class SearchHistoryAdapter(
    private val context: Context,
    private val historyList: List<SearchHistory>,
    private val onItemClick: (SearchHistory) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageViewArtistProfile: ImageView = view.findViewById(R.id.imageViewArtistProfile)
        val textViewArtistName: TextView = view.findViewById(R.id.textViewArtistName)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val historyItem = historyList[position]
                    onItemClick(historyItem)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(context).inflate(R.layout.dialog_search_history_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val historyItem = historyList[position]
        holder.textViewArtistName.text = historyItem.artistName

        Glide.with(context)
            .load(historyItem.profileImageUrl)
            .apply(RequestOptions().circleCrop())
            .into(holder.imageViewArtistProfile)
    }

    override fun getItemCount(): Int {
        return historyList.size
    }
}