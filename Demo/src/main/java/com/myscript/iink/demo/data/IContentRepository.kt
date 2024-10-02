// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.data

import com.myscript.iink.ContentPart
import com.myscript.iink.demo.domain.PartType
import java.io.File

interface IContentRepository {

    /**
     * Returns the identifiers of all the available parts.
     *
     * @return A list of all the parts' identifiers.
     */
    val allParts: List<String>

    /**
     * Returns the identifier of the newly created part.
     * Such identifier can be further reused in other [IContentRepository] APIs.
     *
     * @param partType the type of the part to create.
     * @return the identifier of the newly created part.
     */
    fun createPart(partType: PartType): String

    /**
     * Import all [ContentPart] contained in the given [ContentPackage] file.
     * Each individual [ContentPart] from source [ContentPackage] is split in its own [ContentPackage]
     * locally.
     *
     * @param file the content package where to find content part to import.
     * @return the list of content id imported (that can be used with [getPart]).
     *
     * @see ContentPackage
     * @see ContentPart
     * @see allParts
     */
    fun importContent(file: File): List<String>

    /**
     * Deletes the part file defined by the given identifier.
     *
     * @param contentId the part identifier of the part to consider.
     */
    fun deletePart(contentId: String)

    /**
     * Tells whether the given part identifier departs an existing part or not.
     *
     * @param contentId the part identifier of the part to consider.
     * @return `true` if a part matching the given identifier exists, `false` otherwise.
     */
    fun hasPart(contentId: String): Boolean // TODO remove

    /**
     * Saves the part on disk.
     *
     * @param contentId the part identifier of the part to consider.
     */
    fun savePart(contentId: String)

    /**
     * Loads the content part identified by the given content id.
     *
     * @param contentId the part identifier of the part to consider.
     * @return the content part identified by the given content id.
     */
    fun getPart(contentId: String): ContentPart

    /**
     * Copies the content part within a content package file.
     *
     * @param contentId the part identifier of the part to consider.
     * @param outputDir the directory where to copy the part.
     * @return the file where the part was copied.
     */
    fun copyPart(contentId: String, outputDir: File): File

    var lastOpenedPartId: String?

    var lastChosenPartTypeIndex: Int

    fun requestPartTypes(): List<PartType>

    fun getConfiguration(contentPart: ContentPart): String?
}