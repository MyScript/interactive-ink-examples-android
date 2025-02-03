package com.myscript.iink.uireferenceimplementation;

import com.myscript.iink.ContentSelection;
import com.myscript.iink.Editor;

/**
 * Describes the actions available for a given selection or block.
 *
 * @since 2.0
 */
public enum ContextualActions {
    /**
     * Add block
     */
    ADD_BLOCK,
    /**
     * Remove block or selection
     */
    REMOVE,
    /**
     * Convert.
     * @see Editor#getSupportedTargetConversionStates(ContentSelection)
     */
    CONVERT,
    /**
     * Copy block or selection
     */
    COPY,
    /**
     * Paste current copy
     */
    PASTE,
    /**
     * Export.
     * @see Editor#getSupportedExportMimeTypes(ContentSelection)
     */
    EXPORT,
    /**
     * Change Text blocks format.
     * @see Editor#getSupportedTextFormats(ContentSelection)
     */
    FORMAT_TEXT,
    /**
     * Change selection mode.
     * @see Editor#getAvailableSelectionModes()
     */
    SELECTION_MODE,
    /**
     * Change selection type.
     * @see Editor#getAvailableSelectionTypes(ContentSelection)
     */
    SELECTION_TYPE
}
