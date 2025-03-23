package com.dev.tunedetectivex

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class PlaceholderAdapter : RecyclerView.Adapter<PlaceholderAdapter.PlaceholderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceholderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.placeholder_item, parent, false)
        return PlaceholderViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceholderViewHolder, position: Int) {
    }

    override fun getItemCount(): Int = 10

    class PlaceholderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}