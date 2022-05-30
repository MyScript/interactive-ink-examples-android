// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.myscript.iink.demo.R


class ColorViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.toolbar_color_cell, parent, false)
) {
    private val colorView: ImageView = itemView.findViewById(R.id.toolbar_color_icon)

    fun bind(state: ColorState, onColorSelected: (ColorState) -> Unit) {
        colorView.imageTintList = ColorStateList.valueOf(state.color.opaque)

        itemView.isSelected = state.isSelected
        itemView.setOnClickListener { onColorSelected(state) }
    }

    fun unbind() {
        itemView.setOnClickListener(null)
    }
}

class ColorsAdapter(private val onColorSelected: (ColorState) -> Unit)
    : ListAdapter<ColorState, ColorViewHolder>(COLOR_DIFF_UTIL_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        return ColorViewHolder(parent)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        holder.bind(getItem(position), onColorSelected)
    }

    override fun onViewRecycled(holder: ColorViewHolder) {
        holder.unbind()
    }

    override fun submitList(list: List<ColorState>?) {
        if (list == null) {
            super.submitList(null)
        } else {
            super.submitList(list.toList()) // force DiffUtil by creating a new list
        }
    }

    companion object {
        private val COLOR_DIFF_UTIL_CALLBACK = object : DiffUtil.ItemCallback<ColorState>() {
            override fun areItemsTheSame(oldItem: ColorState, newItem: ColorState): Boolean {
                return oldItem.color == newItem.color
            }

            override fun areContentsTheSame(oldItem: ColorState, newItem: ColorState): Boolean {
                return oldItem == newItem
            }
        }
    }
}