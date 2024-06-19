// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.ui

import androidx.annotation.ColorInt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.viewModelScope
import com.myscript.iink.EditorError
import com.myscript.iink.MimeType
import com.myscript.iink.demo.domain.BlockType
import com.myscript.iink.demo.domain.MenuAction
import com.myscript.iink.demo.domain.PartEditor
import com.myscript.iink.demo.domain.PartType
import com.myscript.iink.demo.domain.PenBrush
import com.myscript.iink.demo.domain.PredictionSettings
import com.myscript.iink.demo.domain.ToolType
import com.myscript.iink.uireferenceimplementation.EditorData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import com.myscript.iink.graphics.Color as IInkColor

val MimeType.primaryFileExtension: String
    get() = fileExtensions.split(',').first()

data class PartHistoryState(val canUndo: Boolean = false, val canRedo: Boolean = false)

data class PartNavigationState(val hasPrevious: Boolean = false, val hasNext: Boolean = false)

sealed class PartState {
    open val isReady: Boolean = false
    open val partId: String? = null
    open val partType: PartType? = null

    object Unloaded : PartState()
    data class Loading(override val partId: String) : PartState()
    data class Loaded(override val partId: String, override val partType: PartType) : PartState() {
        override val isReady: Boolean = true
    }
}

sealed class ContextualActionState {
    data class AddBlock(val x: Float, val y: Float, val items: List<BlockType>) : ContextualActionState()
    data class Action(val x: Float, val y: Float, val items: List<MenuAction>) : ContextualActionState()
    data class Export(val x: Float, val y: Float, val items: List<MimeType>) : ContextualActionState()
}

data class ToolState(
    val type: ToolType,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true
)

enum class Thickness {
    THIN, MEDIUM, LARGE;
}

data class ColorState(@ColorInt val color: Int, val isSelected: Boolean)
data class ThicknessState(val thickness: Thickness, val isSelected: Boolean)
data class PenBrushState(val penBrush: PenBrush, val isSelected: Boolean)

class ColorPalette(
        private val colors: Map<ToolType, List<Int>>
) {
    @ColorInt
    fun getColors(toolType: ToolType): List<Int> {
        return colors[toolType] ?: emptyList()
    }
}

// You could add any further data place holder here (like default name, last chosen recognition language, ...)
data class NewPartRequest(val availablePartTypes: List<PartType>, val defaultPartType: PartType? = null)

data class Error(
    val severity: Severity,
    val title: String,
    val message: String,
    val exception: Exception? = null,
    val error: EditorError? = null,
) {
    enum class Severity { CRITICAL, ERROR, WARNING, NOTIFICATION }

    internal val id = UUID.randomUUID()
}

class EditorViewModel(
    private val partEditor: PartEditor,
    private val colorPalette: ColorPalette
) : ViewModel() {

    private val _availableTools = MutableLiveData<List<ToolState>>(emptyList())
    val availableTools: LiveData<List<ToolState>>
        get() = _availableTools

    private val _availableColors = MutableLiveData<List<ColorState>>(emptyList())
    val availableColors: LiveData<List<ColorState>>
        get() = _availableColors

    private val _availableThicknesses = MutableLiveData<List<ThicknessState>>(emptyList())
    val availableThicknesses: LiveData<List<ThicknessState>>
        get() = _availableThicknesses

    private val _availablePenBrushes = MutableLiveData<List<PenBrushState>>(emptyList())
    val availablePenBrushes: LiveData<List<PenBrushState>>
        get() = _availablePenBrushes

    private val _enableActivePen = MutableLiveData(partEditor.isActivePenEnabled)
    val enableActivePen: LiveData<Boolean>
        get() = _enableActivePen

    private var _partCreationRequest = MutableLiveData<NewPartRequest?>(null)
    val partCreationRequest: LiveData<NewPartRequest?>
        get() = _partCreationRequest

    private var _partState = MutableLiveData<PartState>(PartState.Unloaded)
    val partState: LiveData<PartState>
        get() = _partState

    private var _error = MutableLiveData<Error?>(null)
    val error: LiveData<Error?>
        get() = _error

    private val _partHistoryState = MutableLiveData(PartHistoryState())
    val partHistoryState: LiveData<PartHistoryState>
        get() = _partHistoryState

    private val _partNavigationState = MutableLiveData(PartNavigationState())
    val partNavigationState: LiveData<PartNavigationState>
        get() = _partNavigationState

    private val _toolSheetExpansionState = MutableLiveData(false)
    val toolSheetExpansionState: LiveData<Boolean>
        get() = _toolSheetExpansionState.distinctUntilChanged()

    val predictionSettings: PredictionSettings
        get() = partEditor.getPredictionSettings()

    private val partEditorListener: PartEditor.Listener =
        object : PartEditor.Listener {
            override fun updateUndoRedoState(canUndo: Boolean, canRedo: Boolean) {
                _partHistoryState.value = PartHistoryState(canUndo, canRedo)
                _toolSheetExpansionState.value = false
            }

            override fun updatePartNavigationState(hasPrevious: Boolean, hasNext: Boolean) {
                _partNavigationState.value = PartNavigationState(hasPrevious, hasNext)
            }

            override fun toolChanged(toolType: ToolType?, iinkColor: IInkColor, thickness: Float, penBrush: PenBrush?) {
                val tools: List<ToolState> = _availableTools.value?.map { toolState ->
                    ToolState(toolState.type, toolState.type == toolType, toolState.isEnabled)
                } ?: emptyList()
                _availableTools.value = tools
                _availableColors.value = colorsByTools(toolType, iinkColor)
                _availableThicknesses.value = thicknessesByTools(toolType, thickness)
                _availablePenBrushes.value = penBrushesByTools(toolType, penBrush)
            }

            override fun colorChanged(toolType: ToolType, iinkColor: IInkColor?) {
                _availableColors.value = _availableColors.value?.map { colorState ->
                    ColorState(colorState.color, iinkColor?.androidColor == colorState.color)
                } ?: emptyList()
            }

            override fun thicknessChanged(toolType: ToolType, thickness: Float?) {
                val thicknesses = _availableThicknesses.value?.map { thicknessState ->
                    ThicknessState(thicknessState.thickness, thicknessState.thickness.toFloat(toolType) == thickness)
                }
                _availableThicknesses.value = thicknesses?.toList()
            }

            override fun penBrushChanged(toolType: ToolType, penBrush: PenBrush) {
                val thick = _availablePenBrushes.value?.map { state ->
                    PenBrushState(state.penBrush, penBrush == state.penBrush)
                }
                _availablePenBrushes.value = thick?.toList()
            }

            override fun partLoaded(partId: String, partType: PartType) {
                _partState.value = PartState.Loaded(partId, partType)
            }

            override fun partLoading(partId: String) {
                _partState.value = PartState.Loading(partId)
            }

            override fun partLoadingError(partId: String, exception: Exception) {
                _partState.value = PartState.Unloaded
                notifyError(Error(Error.Severity.ERROR, "Loading error", "Error while loading $partId", exception))
            }

            override fun editorError(blockId: String, error: EditorError, message: String) {
                notifyError(Error(Error.Severity.NOTIFICATION, "Editor error on $blockId", message, error = error))
            }

            override fun updateToolState(partType: PartType, toolType: ToolType, enableActivePen: Boolean) {
                val tools: MutableList<ToolState> = mutableListOf()
                partEditor.getTools(partType, enableActivePen).forEach { (toolType, isEnable) ->
                    tools.add(toolType.toToolState(toolType == ToolType.PEN, isEnable))
                }
                _availableTools.value = tools
            }

            override fun actionError(exception: Exception, blockId: String?) {
                notifyError(Error(Error.Severity.ERROR, "Contextual action error", exception.message.toString(), exception))
            }
        }

    private fun thicknessesByTools(toolType: ToolType?, selectedThickness: Float): List<ThicknessState> {
        return if (toolType == ToolType.HIGHLIGHTER || toolType == ToolType.PEN) {
            Thickness.values().map { ThicknessState(it, it == selectedThickness.toThickness(toolType)) }
        } else {
            emptyList()
        }
    }

    private fun penBrushesByTools(toolType: ToolType?, selectedPenBrush: PenBrush?): List<PenBrushState> {
        return if (toolType == ToolType.PEN) {
            PenBrush.values().map { PenBrushState(it, it == selectedPenBrush) }
        } else {
            emptyList()
        }
    }

    private fun colorsByTools(toolType: ToolType?, selectedColor: IInkColor): List<ColorState> {
        return if (toolType != null) {
            colorPalette.getColors(toolType).map {
                ColorState(it, it.iinkColor == selectedColor)
            }
        } else {
            emptyList()
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            partEditor.loadParts()
        }
        partEditor.setListener(partEditorListener)

        if (partEditor.lastChosenPartType() == null) {
            requestNewPart()
        }
    }

    override fun onCleared() {
        super.onCleared()
        partEditor.saveCurrentPart()
        partEditor.closeEditor()
        partEditor.closePart()
        partEditor.setListener(null)
    }

    private fun notifyError(e: Error) {
        synchronized(_error) {
            _error.value = e
        }
    }

    fun dismissErrorMessage(error: Error) {
        synchronized(_error) {
            val currentError = _error.value
            if (currentError?.id == error.id) {
                _error.value = null
            }
        }
    }

    fun setEditor(editorData: EditorData?) {
        partEditor.setEditor(editorData?.editor, editorData?.inputController, editorData?.extraBrushConfigs)
    }

    fun requestNewPart() {
        val partTypes = partEditor.getPartTypes()
        if (partTypes.isNotEmpty()) {
            val defaultPartType = partEditor.lastChosenPartType() ?: partTypes.first()
            viewModelScope.launch(Dispatchers.Main) {
                _partCreationRequest.value = NewPartRequest(partTypes, defaultPartType)
            }
        }
    }

    fun requestPartTypes(): List<PartType> {
        return partEditor.getPartTypes()
    }

    fun createPart(partType: PartType) {
        viewModelScope.launch(Dispatchers.Main) {
            _partCreationRequest.value = null
        }
        partEditor.saveCurrentPart()
        val partId = partEditor.createPart(partType)
        partEditor.openPart(partId)
    }

    fun importContent(file: File) {
        viewModelScope.launch(Dispatchers.Main) {
            partEditor.importContent(file, ::onContentImportResult)
        }
    }

    private fun onContentImportResult(file: File, partIds: List<String>, exception: Exception?) {
        viewModelScope.launch(Dispatchers.Main) {
            val firstPartId = partIds.firstOrNull()
            when {
                exception != null -> notifyError(Error(Error.Severity.ERROR, "Content import error", "Error while importing ${file.name}", exception))
                firstPartId == null -> notifyError(Error(Error.Severity.WARNING, "Content import issue", "Nothing to import in ${file.name}"))
                else -> partEditor.openPart(firstPartId)
            }
        }
    }

    fun nextPart() {
        partEditor.saveCurrentPart()
        partEditor.nextPart()
    }

    fun previousPart() {
        partEditor.saveCurrentPart()
        partEditor.previousPart()
    }

    fun exportContent(mimeType: MimeType, x: Float?, y: Float?, selectedBlockId: String?, outputDir: File, callback: (File?) -> Unit) {
        val partId = partState.value?.partId
        viewModelScope.launch(Dispatchers.Main) {
            val resultFile = withContext(Dispatchers.Default) {
                val resultFile = File(outputDir, "$partId${mimeType.primaryFileExtension}")
                try {
                    partEditor.exportContent(mimeType, x, y, selectedBlockId, resultFile)
                    resultFile
                } catch (e: Exception) {
                    null
                }
            }
            callback(resultFile)
        }
    }

    fun extractPart(partId: String, outputDir: File, callback: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.Main) {
            val resultFile = withContext(Dispatchers.Default) {
                try {
                    partEditor.copyPart(partId, outputDir)
                } catch (e: Exception) {
                    null
                }
            }
            callback(resultFile)
        }
    }

    fun addImage(file: File, mimeType: String) {
        partEditor.addImage(file, MimeType.fromTypeName(mimeType))
    }

    fun addImage(x: Float, y: Float, file: File, mimeType: String) {
        partEditor.addImageAt(x, y, file, MimeType.fromTypeName(mimeType))
    }

    fun insertText(x: Float, y: Float, text: String) {
        partEditor.insertText(x, y, text)
    }

    fun addBlock(x: Float, y: Float, blockType: BlockType) {
        partEditor.addBlock(x, y, blockType)
    }

    fun requestAddBlockActions(x: Float, y: Float): ContextualActionState {
        val actions = partEditor.getAddBlockActions()
        return ContextualActionState.AddBlock(x, y, actions)
    }

    fun requestFormatTextActions(x: Float, y: Float, selectedBlockId: String? = null): ContextualActionState {
        val actions = partEditor.getFormatTextActions(x, y, selectedBlockId)
        return ContextualActionState.Action(x, y, actions)
    }

    fun requestSmartGuideActions(x: Float, y: Float, contentBlockId: String): ContextualActionState {
        val actions = partEditor.getMenuActions(contentBlockId)
        return ContextualActionState.Action(x, y, actions)
    }

    fun requestContentBlockActions(x: Float, y: Float): ContextualActionState {
        val actions = partEditor.getMenuActions(x, y)
        return ContextualActionState.Action(x, y, actions)
    }

    fun getExportMimeTypes(): List<MimeType> {
        return partEditor.getExportMimeTypes()
    }

    fun requestExportActions(x: Float, y: Float, selectedBlockId: String? = null): ContextualActionState {
        val actions = partEditor.getExportActions(x, y, selectedBlockId)
        return ContextualActionState.Export(x, y, actions)
    }

    fun zoomOut() {
        partEditor.zoomOut()
    }

    fun zoomIn() {
        partEditor.zoomIn()
    }

    fun resetView() {
        partEditor.resetView()
    }

    fun undo() {
        partEditor.undo()
    }

    fun redo() {
        partEditor.redo()
    }

    fun expandColorPalette(expanded: Boolean) {
        if (_toolSheetExpansionState.value == expanded) return
        viewModelScope.launch(Dispatchers.Main) {
            _toolSheetExpansionState.value = expanded
        }
    }

    fun toggleColorPalette() {
        val expanded = _toolSheetExpansionState.value ?: return
        expandColorPalette(!expanded)
    }

    fun changeTool(tool: ToolState) {
        if (partEditor.selectedTool == tool.type) {
            toggleColorPalette()
        } else {
            partEditor.changeTool(tool.type)
        }
    }

    fun changeColor(color: ColorState) {
        partEditor.changeColor(color.color.iinkColor)
    }

    fun changeThickness(thicknessState: ThicknessState) {
        partEditor.selectedTool?.let { partEditor.changeThickness(thicknessState.thickness.toFloat(it)) }
    }

    fun changePenBrush(penBrushState: PenBrushState) {
        partEditor.changePenBrush(penBrushState.penBrush)
    }

    fun enableActivePen(enableActivePen: Boolean) {
        partEditor.enableActivePen(enableActivePen)
        _enableActivePen.value = partEditor.isActivePenEnabled
    }

    fun clearContent() {
        partEditor.clearContent()
    }

    fun convertContent() {
        partEditor.convertContent()
    }

    fun changePredictionSettings(enable: Boolean, durationMs: Int) {
        partEditor.changePredictionSettings(enable, durationMs)
    }

    fun actionMenu(x: Float, y: Float, menuAction: MenuAction, selectedBlockId: String? = null) {
        partEditor.applyActionMenu(x, y, menuAction, selectedBlockId)
    }

    fun save() {
        partEditor.saveCurrentPart()
    }
}
