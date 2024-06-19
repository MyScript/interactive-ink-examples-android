// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.myscript.iink.ContentPart
import com.myscript.iink.Engine
import java.io.File

class ContentRepository(
    private val rootDir: File,
    private val engine: Engine?,
    private val preferences: SharedPreferences
) : IContentRepository {

    init {
        rootDir.mkdirs()
    }

    override val allParts: List<String>
        get() = preferences.getStringSet(ALL_PARTS_TYPE_KEY, null)
                ?.toList()
                ?.sorted()
                ?: emptyList()

    private fun contentFile(contentId: String): File = File(rootDir, "$contentId.iink")

    private fun savePartId(contentId: String) {
        val ids = preferences.getStringSet(ALL_PARTS_TYPE_KEY, null)?.toMutableSet() ?: mutableSetOf()
        ids.add(contentId)
        preferences.edit {
            putStringSet(ALL_PARTS_TYPE_KEY, ids)
        }
    }

    private fun removePartId(contentId: String) {
        val ids = preferences.getStringSet(ALL_PARTS_TYPE_KEY, null)?.toMutableSet() ?: mutableSetOf()
        ids.remove(contentId)
        preferences.edit {
            putStringSet(ALL_PARTS_TYPE_KEY, ids)
        }
    }

    override fun getPart(contentId: String): ContentPart {
        val engine = checkNotNull(engine) { "Cannot get part without valid engine" }

        engine.openPackage(contentFile(contentId)).use { contentPackage ->
            return contentPackage.getPart(0)
        }
    }

    override fun copyPart(contentId: String, outputDir: File): File {
        val engine = checkNotNull(engine) { "Cannot copy part without valid engine" }

        val outputFile = File(outputDir, "${contentId}_${System.currentTimeMillis()}.iink")
        engine.createPackage(outputFile).use { targetPackage ->
            getPart(contentId).use { sourcePart ->
                // enforce a save in case such part is being edited at the moment
                sourcePart.getPackage().save()
                targetPackage.clonePart(sourcePart).also(ContentPart::close)
            }
            targetPackage.save()
        }
        return outputFile
    }

    override fun createPart(partType: String): String {
        val engine = checkNotNull(engine) { "Cannot create part without valid engine" }

        // Here we use a human readable name to ease readability in Demo UI.
        // Ideally, application should use *stable* naming such as a UUID and link such file path to
        // an application data layer (such a Room database) to deal with part metadata (title, type,
        // language, last modification date or anything linked to application logic).
        var index = allParts.size
        var contentId: String
        do {
            ++index
            contentId = "Part$index"
        } while (allParts.contains(contentId))
        engine.createPackage(contentFile(contentId)).use { contentPackage ->
            contentPackage.createPart(partType).also(ContentPart::close)
            // TODO async
            contentPackage.save()
        }
        savePartId(contentId)
        return contentId
    }

    override fun deletePart(contentId: String) {
        removePartId(contentId)
        contentFile(contentId).delete()
    }

    override fun hasPart(contentId: String): Boolean {
        return contentFile(contentId).exists()
    }

    override fun savePart(contentId: String) {
        engine?.openPackage(contentFile(contentId))?.use { contentPackage ->
            contentPackage.save()
        }
    }

    override fun requestPartTypes(): List<String> {
        return engine?.supportedPartTypes?.mapNotNull { it } ?: emptyList()
    }

    override fun importContent(file: File): List<String> {
        val engine = checkNotNull(engine) { "Cannot import content without valid engine" }
        require(file.exists()) { "File '$file' does not exist" }
        val ids = mutableListOf<String>()
        engine.openPackage(file).use { sourcePackage ->
            for (partIndex in 0 until sourcePackage.partCount) {
                sourcePackage.getPart(partIndex).use { sourcePart ->
                    // See `createPart` for part id definition, here made simple for Demo purpose
                    val contentId = "Part${System.currentTimeMillis()}"
                    engine.createPackage(contentFile(contentId)).use { targetPackage ->
                        targetPackage.clonePart(sourcePart).also(ContentPart::close)
                        targetPackage.save()
                    }
                    savePartId(contentId)
                    ids += contentId
                }
            }
        }
        return ids
    }

    override var lastOpenedPartId: String?
        get() = preferences.getString(LAST_PART_ID_KEY, null)
        set(value) {
            preferences.edit {
                putString(LAST_PART_ID_KEY, value)
            }
        }

    override var lastChosenPartType: String?
        get() = preferences.getString(LAST_PART_TYPE_KEY, null)
        set(value) {
            preferences.edit {
                putString(LAST_PART_TYPE_KEY, value)
            }
        }

    companion object {
        private const val LAST_PART_ID_KEY = "iink.demo.lastPartId"
        private const val LAST_PART_TYPE_KEY = "iink.demo.lastPartType"
        private const val ALL_PARTS_TYPE_KEY = "iink.demo.allparts"
    }
}