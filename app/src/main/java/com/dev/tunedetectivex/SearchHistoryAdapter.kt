package com.dev.tunedetectivex

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

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
                    checkNetworkTypeAndSetFlag()
                    onItemClick(historyItem)
                    dialog.dismiss()
                }
            }
        }
    }

    fun checkNetworkTypeAndSetFlag() {
        val sharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val networkType = sharedPreferences.getString("networkType", "Any") ?: "Any"
        val isNetworkRequestsAllowed =
            WorkManagerUtil.isSelectedNetworkTypeAvailable(context, networkType)

        if (!isNetworkRequestsAllowed) {
            Toast.makeText(
                context,
                context.getString(R.string.network_type_not_available),
                Toast.LENGTH_SHORT
            ).show()
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

        val placeholderResId = R.drawable.placeholder_image

        BitmapUtils.loadBitmapFromUrl(
            context as Activity,
            historyItem.profileImageUrl,
            holder.imageViewArtistProfile,
            isCircular = true,
            placeholderResId = placeholderResId
        )
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