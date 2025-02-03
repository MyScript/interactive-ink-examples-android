package com.myscript.iink.demo.domain

import com.myscript.iink.ContentPart
import java.io.File

fun ContentPart.getConfigurationProfile(): String? {
    return metadata.getString("configuration-profile", "").takeIf(String::isNotEmpty)
}

fun ContentPart.setConfigurationProfile(configurationProfile: String?) {
    metadata = metadata.apply {
        setString("configuration-profile", configurationProfile?.takeUnless(String::isNullOrBlank) ?: "")
    }
}

open class PartType(val iinkPartType: String, val configurationProfile: String? = null) {

    data object Diagram : PartType("Diagram")
    data object Math : PartType("Math")
    data object RawContent : PartType("Raw Content")
    data object Text : PartType("Text")
    data object TextDocument : PartType("Text Document")
    data object Drawing : PartType("Drawing")

    override fun toString(): String {
        return if (configurationProfile.isNullOrEmpty()) {
            iinkPartType
        } else {
            "$iinkPartType (${File(configurationProfile).nameWithoutExtension})"
        }
    }
}
