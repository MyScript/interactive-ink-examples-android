// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.util

import android.view.View
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.appcompat.widget.TooltipCompat

fun View.setTooltipText(@StringRes textRes: Int) {
    TooltipCompat.setTooltipText(this, context.getString(textRes))
}

fun ImageView.setContentDescription(@StringRes textRes: Int) {
    contentDescription = context.getString(textRes)
}