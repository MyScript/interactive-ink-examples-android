// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.domain

import com.myscript.iink.Configuration

fun Configuration.enableRawContentInteractivity() {
    // Display grid background
    setString("raw-content.line-pattern", "grid")

    // Activate handwriting recognition for text only
    setBoolean("raw-content.recognition.text", true)
    setBoolean("raw-content.recognition.shape", false)

    // Allow conversion of text
    setBoolean("raw-content.convert.text", true)
    setBoolean("raw-content.convert.node", false)
    setBoolean("raw-content.convert.edge", false)

    // Allow converting shapes by holding the pen in position
    setBoolean("raw-content.convert.shape-on-hold", true)

    // Configure interactions
    setString("raw-content.interactive-items", "converted-or-mixed")
    setBoolean("raw-content.tap-interactions", true)
    setBoolean("raw-content.eraser.erase-precisely", false)
    setBoolean("raw-content.eraser.dynamic-radius", true)
    setBoolean("raw-content.auto-connection", true)

    // Show alignment guides and snap to them
    setBoolean("raw-content.guides.enable", true)
    setBoolean("raw-content.guides.snap", true)

    // Allow gesture detection
    setStringArray("raw-content.pen.gestures", arrayOf("underline", "scratch-out", "strike-through"))
}