// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.myscript.iink.demo.R

private fun ThicknessState.toScale(): Float = when (this.thickness) {
    Thickness.THIN -> .25f
    Thickness.MEDIUM -> .5f
    Thickness.LARGE -> 1f
}

class ThicknessViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.toolbar_thickness_cell, parent, false)
) {
    private val thicknessView: ImageView = itemView.findViewById(R.id.toolbar_thickness_icon)

    fun bind(state: ThicknessState, onThicknessSelected: (ThicknessState) -> Unit) {
        val scale = state.toScale()
        thicknessView.scaleX = scale
        thicknessView.scaleY = scale

        itemView.isSelected = state.isSelected
        itemView.setOnClickListener { onThicknessSelected(state) }
    }

    fun unbind() {
        itemView.setOnClickListener(null)
    }
}

class ThicknessesAdapter(private val onThicknessSelected: (ThicknessState) -> Unit)
    : ListAdapter<ThicknessState, ThicknessViewHolder>(THICKNESS_DIFF_UTIL_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThicknessViewHolder {
        return ThicknessViewHolder(parent)
    }

    override fun onBindViewHolder(holder: ThicknessViewHolder, position: Int) {
        holder.bind(getItem(position), onThicknessSelected)
    }

    override fun onViewRecycled(holder: ThicknessViewHolder) {
        holder.unbind()
    }

    override fun submitList(list: List<ThicknessState>?) {
        if (list == null) {
            super.submitList(null)
        } else {
            super.submitList(list.toList()) // force DiffUtil by creating a new list
        }
    }

    companion object {
        private val THICKNESS_DIFF_UTIL_CALLBACK = object : DiffUtil.ItemCallback<ThicknessState>() {
            override fun areItemsTheSame(oldItem: ThicknessState, newItem: ThicknessState): Boolean {
                return oldItem.thickness == newItem.thickness
            }

            override fun areContentsTheSame(oldItem: ThicknessState, newItem: ThicknessState): Boolean {
                return oldItem == newItem
            }
        }
    }
}