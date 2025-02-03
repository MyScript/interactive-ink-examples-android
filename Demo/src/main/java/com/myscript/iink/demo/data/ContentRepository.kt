// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.data

import android.content.SharedPreferences
import android.content.res.AssetManager
import androidx.core.content.edit
import com.myscript.iink.ContentPart
import com.myscript.iink.Engine
import com.myscript.iink.demo.domain.PartType
import com.myscript.iink.demo.domain.getConfigurationProfile
import com.myscript.iink.demo.domain.setConfigurationProfile
import java.io.File
import java.io.FileNotFoundException
import java.io.Reader

class ContentRepository(
    private val rootDir: File,
    private val engine: Engine?,
    private val preferences: SharedPreferences,
    private val assetManager: AssetManager? = null
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

    override fun createPart(partType: PartType): String {
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
            contentPackage.createPart(partType.iinkPartType).use { part ->
                part.setConfigurationProfile(partType.configurationProfile)
            }
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

    override fun requestPartTypes(): List<PartType> {
        val configurationProfiles = mutableListOf<PartType>()

        engine?.supportedPartTypes?.forEach { partName ->
            if (partName != null) {
                configurationProfiles.add(PartType(partName))

                val availableProfiles = assetManager?.list(File(CONFIGURATION_PROFILE_DIRECTORY, partName).path)
                if (!availableProfiles.isNullOrEmpty()) {
                    availableProfiles.forEach { profile ->
                        configurationProfiles.add(PartType(partName, File(profile).nameWithoutExtension))
                    }
                }
            }
        }
        return configurationProfiles
    }

    override fun getConfiguration(contentPart: ContentPart): String? {
        val assetManager = assetManager ?: return null

        val defaultFile = File(CONFIGURATION_PROFILE_DIRECTORY, DEFAULT_RAW_CONTENT_CONFIGURATION_FILE_NAME)

        val configurationProfile = contentPart.getConfigurationProfile()
        val configurationFile = if (configurationProfile != null) {
            val parent = File(CONFIGURATION_PROFILE_DIRECTORY, contentPart.type.toString())
            val confFile = assetManager.list(parent.path)?.firstOrNull {
                File(it).nameWithoutExtension == configurationProfile
            }
            if (confFile != null) {
                File(parent, confFile)
            } else {
                defaultFile
            }
        } else {
            defaultFile
        }

        return try {
            assetManager.open(configurationFile.path).bufferedReader().use(Reader::readText)
        } catch (e: FileNotFoundException) {
            null
        }
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

    override var lastChosenPartTypeIndex: Int
        get() = preferences.getInt(LAST_PART_TYPE_INDEX_KEY, -1)
        set(value) {
            preferences.edit {
                putInt(LAST_PART_TYPE_INDEX_KEY, value)
            }
        }

    companion object {
        private const val LAST_PART_ID_KEY = "iink.demo.lastPartId"
        private const val LAST_PART_TYPE_INDEX_KEY = "iink.demo.lastPartTypeIndex"
        private const val ALL_PARTS_TYPE_KEY = "iink.demo.allparts"

        private const val CONFIGURATION_PROFILE_DIRECTORY = "parts"
        private const val DEFAULT_RAW_CONTENT_CONFIGURATION_FILE_NAME = "interactivity.json"
    }
}