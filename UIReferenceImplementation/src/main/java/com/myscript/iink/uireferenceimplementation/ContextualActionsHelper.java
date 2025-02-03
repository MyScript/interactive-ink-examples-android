// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import com.myscript.iink.ContentBlock;
import com.myscript.iink.ContentPart;
import com.myscript.iink.ContentSelection;
import com.myscript.iink.ContentSelectionMode;
import com.myscript.iink.Editor;

import java.util.EnumSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ContextualActionsHelper
{
    private ContextualActionsHelper()
    {
        // utility class
    }

    @NonNull
    public static EnumSet<ContextualActions> getAvailableActionsForBlock(@NonNull Editor editor, @NonNull ContentBlock block)
    {
        EnumSet<ContextualActions> actions = EnumSet.noneOf(ContextualActions.class);

        try (ContentBlock rootBlock = editor.getRootBlock())
        {
            ContentPart part = editor.getPart();
            boolean isRootBlock = rootBlock != null && block.getId().equals(rootBlock.getId());
            boolean onTextDocument = "Text Document".equals(part != null ? part.getType() : null);
            boolean blockIsEmpty = editor.isEmpty(block);

            boolean displayAddBlock = editor.getSupportedAddBlockTypes().length > 0 && (!onTextDocument || isRootBlock);
            boolean displayRemove = !isRootBlock;
            boolean displayCopy = !isRootBlock || !onTextDocument;
            boolean displayConvert = !blockIsEmpty && editor.getSupportedTargetConversionStates(block).length > 0;
            boolean displayExport = editor.getSupportedExportMimeTypes(block).length > 0;
            boolean displayFormatText = editor.getSupportedTextFormats(block).size() > 0;
            boolean displaySelectionMode = editor.getAvailableSelectionModes().size() > 0;
            boolean displaySelectionType = editor.getAvailableSelectionTypes(block).length > 0;

            if (displayAddBlock) actions.add(ContextualActions.ADD_BLOCK);
            if (displayRemove) actions.add(ContextualActions.REMOVE);
            if (displayConvert) actions.add(ContextualActions.CONVERT);
            if (displayCopy) actions.add(ContextualActions.COPY);
            if (isRootBlock) actions.add(ContextualActions.PASTE);
            if (displayExport) actions.add(ContextualActions.EXPORT);
            if (displayFormatText) actions.add(ContextualActions.FORMAT_TEXT);
            if (displaySelectionMode) actions.add(ContextualActions.SELECTION_MODE);
            if (displaySelectionType) actions.add(ContextualActions.SELECTION_TYPE);
        }
        return actions;
    }

    @NonNull
    public static EnumSet<ContextualActions> getAvailableActionsForSelection(@NonNull Editor editor, @Nullable ContentSelection selection)
    {
        EnumSet<ContextualActions> actions = EnumSet.noneOf(ContextualActions.class);

        boolean displayConvert = editor.getSupportedTargetConversionStates(selection).length > 0;
        boolean displayExport = editor.getSupportedExportMimeTypes(selection).length > 0;
        boolean displayFormatText = selection != null && !editor.getSupportedTextFormats(selection).isEmpty();
        boolean displaySelectionMode = editor.getAvailableSelectionModes().size() > 0;
        boolean displaySelectionType = editor.getAvailableSelectionTypes(selection).length > 0;

        actions.add(ContextualActions.REMOVE);
        if (displayConvert) actions.add(ContextualActions.CONVERT);
        actions.add(ContextualActions.COPY);
        if (displayExport) actions.add(ContextualActions.EXPORT);
        if (displayFormatText) actions.add(ContextualActions.FORMAT_TEXT);
        if (displaySelectionMode) actions.add(ContextualActions.SELECTION_MODE);
        if (displaySelectionType) actions.add(ContextualActions.SELECTION_TYPE);

        return actions;
    }
}