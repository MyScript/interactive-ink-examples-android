// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ui

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.myscript.iink.demo.domain.ToolType
import com.myscript.iink.graphics.Color as IInkColor

@get:ColorInt
val IInkColor.androidColor: Int
    get() = Color.argb(a(), r(), g(), b())

val Int.iinkColor: IInkColor
    get() {
        val r = this shr 16 and 0xff
        val g = this shr 8 and 0xff
        val b = this and 0xff
        val a = this shr 24 and 0xff
        return IInkColor(r, g, b, a)
    }

@get:ColorInt
val Int.opaque: Int
    get() = ColorUtils.setAlphaComponent(this, 0xFF)

fun ToolType.toToolState(isSelected: Boolean, isEnable: Boolean) = ToolState(this, isSelected, isEnable)

fun Thickness.toFloat(toolType: ToolType) = when (toolType) {
    ToolType.PEN -> when (this) {
        Thickness.THIN -> .25f
        Thickness.MEDIUM -> .65f
        Thickness.LARGE -> 1.65f
    }
    ToolType.HIGHLIGHTER -> when (this) {
        Thickness.THIN -> 1.67f
        Thickness.MEDIUM -> 5f
        Thickness.LARGE -> 15f
    }
    else -> 0f
}

fun Float.toThickness(toolType: ToolType?) = when (toolType) {
    ToolType.PEN -> when {
        this <= .25f -> Thickness.THIN
        this == .65f -> Thickness.MEDIUM
        this >= 1.65f -> Thickness.LARGE
        else -> Thickness.THIN
    }
    ToolType.HIGHLIGHTER -> when {
        this <= 1.67f -> Thickness.THIN
        this == 5f -> Thickness.MEDIUM
        this >= 15f -> Thickness.LARGE
        else -> null
    }
    else -> Thickness.MEDIUM
}
