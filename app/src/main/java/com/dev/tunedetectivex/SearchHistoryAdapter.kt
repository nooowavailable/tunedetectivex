package com.dev.tunedetectivex

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SearchHistoryAdapter(
    private val context: Context,
    private val historyList: MutableList<SearchHistory>,
    private val onItemClick: (SearchHistory) -> Unit,
    private val dialog: Dialog
) : RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageViewArtistProfile: ImageView = view.findViewById(R.id.imageViewArtistProfile)
        val textViewArtistName: TextView = view.findViewById(R.id.textViewArtistName)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val historyItem = historyList[position]

                    if (isNetworkTypeAllowed()) {
                        onItemClick(historyItem)
                        dialog.dismiss()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.network_type_not_available),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun isNetworkTypeAllowed(): Boolean {
        val sharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"
        return WorkManagerUtil.isSelectedNetworkTypeAvailable(context, networkType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(context).inflate(R.layout.dialog_search_history_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val historyItem = historyList[position]
        holder.textViewArtistName.text = historyItem.artistName

        if (isNetworkTypeAllowed()) {
            Glide.with(context)
                .load(historyItem.profileImageUrl)
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .circleCrop()
                .into(holder.imageViewArtistProfile)
        } else {
            holder.imageViewArtistProfile.setImageResource(R.drawable.placeholder_image)
        }
    }

    override fun getItemCount(): Int {
        return historyList.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshHistory(newHistory: List<SearchHistory>) {
        historyList.clear()

        newHistory.forEach { newItem ->
            if (!historyList.any { it.id == newItem.id }) {
                historyList.add(newItem)
            } else {
                val existingItemIndex = historyList.indexOfFirst { it.id == newItem.id }
                if (existingItemIndex != -1) {
                    historyList[existingItemIndex] = newItem
                }
            }
        }

        notifyDataSetChanged()
    }
}