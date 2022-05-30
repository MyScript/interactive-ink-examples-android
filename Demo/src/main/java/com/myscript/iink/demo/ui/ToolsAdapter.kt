// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.myscript.iink.demo.R
import com.myscript.iink.demo.domain.ToolType

private val ToolType.asDrawable: Int
    get() = when (this) {
        ToolType.HAND -> R.drawable.ic_hand_outlined
        ToolType.PEN -> R.drawable.ic_pen_outlined
        ToolType.ERASER -> R.drawable.ic_eraser
        ToolType.HIGHLIGHTER -> R.drawable.ic_brush_outlined
        ToolType.LASSO -> R.drawable.ic_lasso
    }

class ToolStateViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.toolbar_tool_cell, parent, false)
) {
    private val toolView: ImageView = itemView.findViewById(R.id.toolbar_tool_icon)

    fun bind(state: ToolState, onToolSelected: (ToolState) -> Unit) {
        toolView.setImageResource(state.type.asDrawable)
        toolView.isEnabled = state.isEnabled

        itemView.isSelected = state.isSelected
        itemView.setOnClickListener { onToolSelected(state) }
    }

    fun unbind() {
        itemView.setOnClickListener(null)
    }
}

class ToolsAdapter(private val onToolSelected: (ToolState) -> Unit)
    : ListAdapter<ToolState, ToolStateViewHolder>(TOOL_DIFF_UTIL_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolStateViewHolder {
        return ToolStateViewHolder(parent)
    }

    override fun onBindViewHolder(holder: ToolStateViewHolder, position: Int) {
        holder.bind(getItem(position), onToolSelected)
    }

    override fun onViewRecycled(holder: ToolStateViewHolder) {
        holder.unbind()
    }

    override fun submitList(list: List<ToolState>?) {
        if (list == null) {
            super.submitList(null)
        } else {
            super.submitList(list.toList()) // force DiffUtil by creating a new list
        }
    }

    companion object {
        private val TOOL_DIFF_UTIL_CALLBACK = object : DiffUtil.ItemCallback<ToolState>() {
            override fun areItemsTheSame(oldItem: ToolState, newItem: ToolState): Boolean {
                return oldItem.type == newItem.type
            }

            override fun areContentsTheSame(oldItem: ToolState, newItem: ToolState): Boolean {
                return oldItem == newItem
            }
        }
    }
}