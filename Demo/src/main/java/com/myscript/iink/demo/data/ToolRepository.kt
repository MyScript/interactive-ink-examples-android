// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.data

import android.content.SharedPreferences
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.content.edit

class ToolRepository(private val preferences: SharedPreferences) {

    @ColorInt
    fun getToolColor(toolKey: String): Int {
        return preferences.getInt("$toolKey-color", Color.TRANSPARENT)
    }

    fun saveToolColor(toolKey: String, @ColorInt color: Int) {
        preferences.edit {
            putInt("$toolKey-color", color)
        }
    }

    fun getToolThickness(toolKey: String): Float {
        return preferences.getFloat("$toolKey-thickness", 0f)
    }

    fun saveToolThickness(toolKey: String, thickness: Float) {
        preferences.edit {
            putFloat("$toolKey-thickness", thickness)
        }
    }
}
