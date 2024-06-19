// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.domain

import com.myscript.iink.Configuration

fun Configuration.enableRawContentInteractivity() {
    // Display grid background
    setString("raw-content.line-pattern", "grid")

    // Activate handwriting recognition for text only
    setStringArray("raw-content.recognition.types", arrayOf("text"))

    // Allow converting shapes by holding the pen in position
    setBoolean("raw-content.convert.shape-on-hold", true)

    // Configure shapes axis snapping
    setStringArray("raw-content.shape.snap-axis", arrayOf("triangle", "rectangle", "rhombus", "parallelogram", "ellipse"))

    // Configure interactions
    setStringArray("raw-content.interactive-blocks.auto-classified", arrayOf<String>())
    setBoolean("raw-content.eraser.erase-precisely", false)
    setBoolean("raw-content.eraser.dynamic-radius", true)
    setBoolean("raw-content.auto-connection", true)
    setStringArray("raw-content.edge.policy", arrayOf("default-with-drag"))

    // Show alignment guides and snap to them
    setStringArray("raw-content.guides.show", arrayOf("alignment", "text", "square", "square-inside", "image-aspect-ratio", "rotation"))
    setStringArray("raw-content.guides.snap", arrayOf("alignment", "text", "square", "square-inside", "image-aspect-ratio", "rotation"))

    // Allow gesture detection
    setStringArray("raw-content.pen.gestures", arrayOf("underline", "scratch-out", "strike-through"))

    // Allow shape & image rotation
    setStringArray("raw-content.rotation", arrayOf("shape", "image"))
}
