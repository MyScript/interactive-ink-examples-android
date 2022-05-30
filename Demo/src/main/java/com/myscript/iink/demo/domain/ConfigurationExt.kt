// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.domain

import com.myscript.iink.Configuration

fun Configuration.enableRawContentConversion() {
    // Activate handwriting recognition for text and shapes
    setBoolean("raw-content.recognition.text", true)
    setBoolean("raw-content.recognition.shape", true)
    // Allow conversion of text, nodes and edges
    setBoolean("raw-content.convert.node", true)
    setBoolean("raw-content.convert.text", true)
    setBoolean("raw-content.convert.edge", true)
    // Allow converting shapes by holding the pen in position
    setBoolean("raw-content.convert.shape-on-hold", true)
    // Allow interactions
    setBoolean("raw-content.tap-interactions", true)
    setBoolean("raw-content.eraser.erase-precisely", false)
    // Show alignment guides and snap to them
    setBoolean("raw-content.guides.enable", true)
    setBoolean("raw-content.guides.snap", true)
    // Allow gesture detection
    setStringArray("raw-content.pen.gestures", arrayOf("underline", "double-underline", "scratch-out", "join", "insert", "strike-through"))
}