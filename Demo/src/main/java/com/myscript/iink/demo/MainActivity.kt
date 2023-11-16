// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DimenRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.coroutineScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.myscript.iink.Editor
import com.myscript.iink.Engine
import com.myscript.iink.MimeType
import com.myscript.iink.demo.databinding.MainActivityBinding
import com.myscript.iink.demo.di.EditorViewModelFactory
import com.myscript.iink.demo.domain.BlockType
import com.myscript.iink.demo.domain.MenuAction
import com.myscript.iink.demo.domain.PartType
import com.myscript.iink.demo.ui.ColorState
import com.myscript.iink.demo.ui.ColorsAdapter
import com.myscript.iink.demo.ui.ContextualActionState
import com.myscript.iink.demo.ui.EditorViewModel
import com.myscript.iink.demo.ui.Error
import com.myscript.iink.demo.ui.NewPartRequest
import com.myscript.iink.demo.ui.NewPredictionRequest
import com.myscript.iink.demo.ui.PartHistoryState
import com.myscript.iink.demo.ui.PartNavigationState
import com.myscript.iink.demo.ui.PartState
import com.myscript.iink.demo.ui.ThicknessState
import com.myscript.iink.demo.ui.ThicknessesAdapter
import com.myscript.iink.demo.ui.ToolState
import com.myscript.iink.demo.ui.ToolsAdapter
import com.myscript.iink.demo.ui.primaryFileExtension
import com.myscript.iink.demo.util.launchActionChoiceDialog
import com.myscript.iink.demo.util.launchPredictionDialog
import com.myscript.iink.demo.util.launchSingleChoiceDialog
import com.myscript.iink.demo.util.launchTextBlockInputDialog
import com.myscript.iink.uireferenceimplementation.EditorView
import com.myscript.iink.uireferenceimplementation.FrameTimeEstimator
import com.myscript.iink.uireferenceimplementation.IInputControllerListener
import com.myscript.iink.uireferenceimplementation.SmartGuideView
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

suspend fun Context.processUriFile(uri: Uri, file: File, logic: (File) -> Unit) {
    withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }
    try {
        logic(file)
    } finally {
        file.deleteOnExit()
    }
}

@get:StringRes
private val MenuAction.stringRes: Int
    get() = when (this) {
        MenuAction.COPY -> R.string.editor_action_copy
        MenuAction.PASTE -> R.string.editor_action_paste
        MenuAction.DELETE -> R.string.editor_action_delete
        MenuAction.CONVERT -> R.string.editor_action_convert
        MenuAction.EXPORT -> R.string.editor_action_export
        MenuAction.ADD_BLOCK -> R.string.editor_action_add_block
        MenuAction.FORMAT_TEXT -> R.string.editor_action_format_text
        MenuAction.FORMAT_TEXT_H1 -> R.string.editor_action_format_text_as_heading1
        MenuAction.FORMAT_TEXT_H2 -> R.string.editor_action_format_text_as_heading2
        MenuAction.FORMAT_TEXT_PARAGRAPH -> R.string.editor_action_format_text_as_paragraph
        MenuAction.FORMAT_TEXT_LIST_BULLET -> R.string.editor_action_format_text_as_list_bullet
        MenuAction.FORMAT_TEXT_LIST_CHECKBOX -> R.string.editor_action_format_text_as_list_checkbox
        MenuAction.FORMAT_TEXT_LIST_NUMBERED -> R.string.editor_action_format_text_as_list_numbered
    }

class MainActivity : AppCompatActivity() {

    private val exportsDir: File
        get() = File(cacheDir, "exports").apply(File::mkdirs)
    private val binding by lazy { MainActivityBinding.inflate(layoutInflater) }
    private var editorView: EditorView? = null
    private val viewModel: EditorViewModel by viewModels { EditorViewModelFactory() }
    private var navigationState: PartNavigationState = PartNavigationState()
    private var partState: PartState = PartState.Unloaded
    private val editorBinding = IInkApplication.DemoModule.editorBinding
    private var smartGuideView: SmartGuideView? = null
    private var toolsAdapter = ToolsAdapter { viewModel.changeTool(it) }
    private var colorsAdapter = ColorsAdapter { viewModel.changeColor(it) }
    private var thicknessesAdapter = ThicknessesAdapter { viewModel.changeThickness(it) }
    private var addImagePosition: PointF? = null

    private companion object {
        const val EnableCapturePrediction: Boolean = true
        const val MinPredictionDurationMs: Int = 16 // 1 frame @60Hz, 2 frames @120Hz
    }

    private val onEditorLongPress = IInputControllerListener { x, y, _ ->
        val actionState = viewModel.requestContentBlockActions(x, y)
        showContextualActionDialog(actionState)
        true
    }

    private val onSmartGuideMenuAction = SmartGuideView.MenuListener { x, y, blockId ->
        val actionState = viewModel.requestSmartGuideActions(x, y, blockId)
        showContextualActionDialog(actionState, blockId)
    }

    private val onBottomSheetStateChanged = object : BottomSheetBehavior.BottomSheetCallback() {
        @SuppressLint("SwitchIntDef")
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            when (newState) {
                BottomSheetBehavior.STATE_COLLAPSED -> viewModel.expandColorPalette(false)
                BottomSheetBehavior.STATE_EXPANDED -> viewModel.expandColorPalette(true)
            }
        }
        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
    }

    private val importIInkFileRequest = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val mimeType = DocumentFile.fromSingleUri(this@MainActivity, uri)?.type ?: contentResolver.getType(uri)
        when (mimeType) {
            "binary/octet-stream",
            "application/zip",
            "application/octet-stream",
            "application/binary",
            "application/x-zip" -> lifecycle.coroutineScope.launch {
                processUriFile(uri, File(cacheDir, "import.iink")) { file ->
                    viewModel.importContent(file)
                }
            }
            else -> onError(Error(
                Error.Severity.WARNING,
                getString(R.string.app_error_unsupported_file_type_title),
                getString(R.string.app_error_unsupported_iink_file_type_message, mimeType)
            ))
        }
    }

    private val importImageRequest = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val mimeType = DocumentFile.fromSingleUri(this@MainActivity, uri)?.type ?: contentResolver.getType(uri)
        when (mimeType) {
            "image/png",
            "image/jpeg" -> lifecycle.coroutineScope.launch {
                val extension = MimeType.fromTypeName(mimeType).primaryFileExtension
                processUriFile(uri, File(cacheDir, "image$extension")) { image ->
                    val pos = addImagePosition
                    addImagePosition = null
                    if (pos != null) {
                        viewModel.addImage(pos.x, pos.y, image, mimeType)
                    } else {
                        viewModel.addImage(image, mimeType)
                    }
                }
            }
            else -> onError(Error(
                Error.Severity.WARNING,
                getString(R.string.app_error_unsupported_file_type_title),
                getString(R.string.app_error_unsupported_image_type_message, mimeType)
            ))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        editorView = findViewById(com.myscript.iink.uireferenceimplementation.R.id.editor_view)
        smartGuideView = findViewById(com.myscript.iink.uireferenceimplementation.R.id.smart_guide_view)

        setSupportActionBar(binding.toolbar)

        viewModel.enableActivePen.observe(this) { activePenEnabled ->
            binding.editorToolbar.switchActivePen.isChecked = activePenEnabled
        }
        viewModel.error.observe(this, this::onError)
        viewModel.toolSheetExpansionState.observe(this, this::onToolSheetExpansionStateUpdate)
        viewModel.availableTools.observe(this, this::onAvailableToolsUpdate)
        viewModel.availableColors.observe(this, this::onAvailableColorsUpdate)
        viewModel.availableThicknesses.observe(this, this::onAvailableThicknessesUpdate)
        viewModel.partCreationRequest.observe(this, this::onPartCreationRequest)
        viewModel.predictionSettingsRequest.observe(this, this::onPredictionSettingsRequest)
        viewModel.partState.observe(this, this::onPartStateUpdate)
        viewModel.partHistoryState.observe(this, this::onPartHistoryUpdate)
        viewModel.partNavigationState.observe(this, this::onPartNavigationStateUpdate)

        val editorData = editorBinding.openEditor(editorView)
        editorData.inputController?.listener = onEditorLongPress
        editorData.inputController?.setViewListener(editorView)
        editorData.editor?.let { editor ->
            viewModel.setEditor(editorData)
            setMargins(editor, R.dimen.editor_horizontal_margin, R.dimen.editor_vertical_margin)
            editorView?.let {
                enableCaptureStrokePrediction(editor.engine, editorView!!.context)
            }
            smartGuideView?.setEditor(editor)
        }
        smartGuideView?.setMenuListener(onSmartGuideMenuAction)
        smartGuideView?.setTypeface(IInkApplication.DemoModule.defaultTypeface)

        with(binding.editorToolbarSheet) {
            toolbarTools.adapter = toolsAdapter
            toolbarColors.itemAnimator = null
            toolbarColors.adapter = colorsAdapter
            toolbarThicknesses.adapter = thicknessesAdapter
        }

        // Note: could be managed by domain layer and handled through observable error channel
        // but kept simple as is to avoid adding too much complexity for this special (unrecoverable) error case
        if (IInkApplication.DemoModule.engine == null) {
            // the certificate provided in `DemoModule.provideEngine` is most likely incorrect
            onError(Error(
                Error.Severity.CRITICAL,
                getString(R.string.app_error_invalid_certificate_title),
                getString(R.string.app_error_invalid_certificate_message)
            ))
        }
    }

    private fun setMargins(editor: Editor, @DimenRes horizontalMarginRes: Int, @DimenRes verticalMarginRes: Int) {
        val displayMetrics = resources.displayMetrics
        with (editor.configuration) {
            val verticalMargin = resources.getDimension(verticalMarginRes)
            val horizontalMargin = resources.getDimension(horizontalMarginRes)
            val verticalMarginMM = 25.4f * verticalMargin / displayMetrics.ydpi
            val horizontalMarginMM = 25.4f * horizontalMargin / displayMetrics.xdpi
            setNumber("text.margin.top", verticalMarginMM)
            setNumber("text.margin.left", horizontalMarginMM)
            setNumber("text.margin.right", horizontalMarginMM)
            setNumber("math.margin.top", verticalMarginMM)
            setNumber("math.margin.bottom", verticalMarginMM)
            setNumber("math.margin.left", horizontalMarginMM)
            setNumber("math.margin.right", horizontalMarginMM)
        }
    }

    private fun enableCaptureStrokePrediction(engine: Engine, context: Context) {
        val durationMs = FrameTimeEstimator.getFrameTime(context).roundToInt()
            .coerceAtLeast(MinPredictionDurationMs)
        with(engine.configuration) {
            setNumber("renderer.prediction.duration", durationMs)
            setBoolean("renderer.prediction.enable", EnableCapturePrediction)
        }
    }

    private fun showContextualActionDialog(actionState: ContextualActionState, selectedBlockId: String? = null) {
        when (actionState) {
            is ContextualActionState.AddBlock -> {
                val blockTypes = actionState.items
                launchActionChoiceDialog(blockTypes.map(BlockType::toString)) { selected ->
                    when (val blockType = blockTypes[selected]) {
                        BlockType.Image -> {
                            addImagePosition = PointF(actionState.x, actionState.y)
                            importImageRequest.launch("image/*")
                        }
                        BlockType.Text -> {
                            // Ensure bottom sheet is collapsed to avoid weird state when IME is dismissed.
                            viewModel.expandColorPalette(false)
                            launchTextBlockInputDialog { text ->
                                viewModel.insertText(actionState.x, actionState.y, text)
                            }
                        }
                        else ->viewModel.addBlock(actionState.x, actionState.y, blockType)
                    }
                }
            }
            is ContextualActionState.Action -> {
                val actions = actionState.items
                launchActionChoiceDialog(actions.map { getString(it.stringRes) }) { selected ->
                    when (val action = actions[selected]) {
                        MenuAction.ADD_BLOCK -> {
                            val blocks = viewModel.requestAddBlockActions(actionState.x, actionState.y)
                            showContextualActionDialog(blocks)
                        }
                        MenuAction.FORMAT_TEXT -> {
                            val formatTexts = viewModel.requestFormatTextActions(actionState.x, actionState.y, selectedBlockId)
                            showContextualActionDialog(formatTexts, selectedBlockId)
                        }
                        MenuAction.EXPORT -> {
                            val mimeTypes = viewModel.requestExportActions(actionState.x, actionState.y, selectedBlockId)
                            showContextualActionDialog(mimeTypes, selectedBlockId)
                        }
                        else -> viewModel.actionMenu(actionState.x, actionState.y, action, selectedBlockId)
                    }
                }
            }
            is ContextualActionState.Export -> onExport(actionState.items, actionState.x, actionState.y, selectedBlockId)
        }
    }

    override fun onStart() {
        super.onStart()

        with(binding.editorToolbar) {
            switchActivePen.setOnCheckedChangeListener { _, isChecked ->
                viewModel.enableActivePen(isChecked)
            }
            editorUndo.setOnClickListener { viewModel.undo() }
            editorRedo.setOnClickListener { viewModel.redo() }
            editorZoomIn.setOnClickListener { viewModel.zoomIn() }
            editorZoomOut.setOnClickListener { viewModel.zoomOut() }
            editorResetView.setOnClickListener { viewModel.resetView() }
            editorClearContent.setOnClickListener { viewModel.clearContent() }
        }

        with(binding.editorToolbarSheet) {
            BottomSheetBehavior.from(toolbarSettingsBottomSheet).addBottomSheetCallback(onBottomSheetStateChanged)
            toolbarSettingsBottomSheet.setOnClickListener {
                viewModel.toggleColorPalette()
            }
        }
    }

    override fun onStop() {
        with(binding.editorToolbar) {
            switchActivePen.setOnCheckedChangeListener(null)
            editorUndo.setOnClickListener(null)
            editorRedo.setOnClickListener(null)
            editorZoomIn.setOnClickListener(null)
            editorZoomOut.setOnClickListener(null)
            editorResetView.setOnClickListener(null)
            editorClearContent.setOnClickListener(null)
        }

        with(binding.editorToolbarSheet) {
            BottomSheetBehavior.from(toolbarSettingsBottomSheet).removeBottomSheetCallback(onBottomSheetStateChanged)
            toolbarSettingsBottomSheet.setOnClickListener(null)
        }
        super.onStop()
    }

    override fun onDestroy() {
        smartGuideView?.setEditor(null)
        smartGuideView?.setMenuListener(null)
        viewModel.setEditor(null)
        super.onDestroy()
    }

    private fun onError(error: Error?) {
        if (error != null) {
            Log.e("MainActivity", error.toString(), error.exception)
        }
        when (error?.severity) {
            null -> Unit
            Error.Severity.CRITICAL ->
                AlertDialog.Builder(this)
                        .setTitle(error.title)
                        .setMessage(error.message)
                        .setPositiveButton(R.string.dialog_ok, null)
                        .show()
            else ->
                // Note: `EditorError` (if any) could be used to specialize the notification (adjust string, localize, notification nature, ...)
                Snackbar.make(binding.root, getString(R.string.app_error_notification, error.severity.name, error.message), Snackbar.LENGTH_LONG)
                        .setAnchorView(binding.editorToolbarSheet.toolbarSettingsBottomSheet)
                        .addCallback(object : Snackbar.Callback() {
                            override fun onDismissed(snackbar: Snackbar?, event: Int) {
                                snackbar?.removeCallback(this)
                                viewModel.dismissErrorMessage(error)
                            }
                        })
                        .show()
        }
    }

    private fun onToolSheetExpansionStateUpdate(expanded: Boolean) {
        with(BottomSheetBehavior.from(binding.editorToolbarSheet.toolbarSettingsBottomSheet)) {
            state = if (expanded) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun onAvailableToolsUpdate(toolStates: List<ToolState>) {
        toolsAdapter.submitList(toolStates)
        binding.editorToolbarSheet.toolbarTools.isVisible = toolStates.isNotEmpty()
    }

    private fun onAvailableColorsUpdate(colorStates: List<ColorState>) {
        colorsAdapter.submitList(colorStates)
        binding.editorToolbarSheet.toolbarColors.isVisible = !colorStates.isNullOrEmpty()
    }

    private fun onAvailableThicknessesUpdate(thicknessStates: List<ThicknessState>) {
        thicknessesAdapter.submitList(thicknessStates)
        binding.editorToolbarSheet.toolbarThicknesses.isVisible = !thicknessStates.isNullOrEmpty()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.let {
            it.findItem(R.id.nav_menu_new_part).isEnabled = viewModel.requestPartTypes().isNotEmpty()
            it.findItem(R.id.nav_menu_previous_part).isEnabled = navigationState.hasPrevious
            it.findItem(R.id.nav_menu_next_part).isEnabled = navigationState.hasNext
            it.findItem(R.id.editor_menu_convert).isEnabled = partState.isReady
            it.findItem(R.id.editor_menu_prediction).isEnabled = true
            it.findItem(R.id.editor_menu_export).isEnabled = partState.isReady
            it.findItem(R.id.editor_menu_save).isEnabled = partState.isReady
            it.findItem(R.id.editor_menu_import_file).isEnabled = true
            it.findItem(R.id.editor_menu_share_file).isEnabled = partState.isReady
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_menu_new_part -> viewModel.requestNewPart()
            R.id.nav_menu_previous_part -> viewModel.previousPart()
            R.id.nav_menu_next_part -> viewModel.nextPart()
            R.id.editor_menu_convert -> viewModel.convertContent()
            R.id.editor_menu_prediction -> viewModel.requestPredictionSettings()
            R.id.editor_menu_export -> onExport(viewModel.getExportMimeTypes())
            R.id.editor_menu_save -> (partState as? PartState.Loaded)?.let { viewModel.save() }
            // Note: ideally we could restrict to `application/*` but some file managers use `binary/octet-stream`
            R.id.editor_menu_import_file -> importIInkFileRequest.launch("*/*")
            R.id.editor_menu_share_file -> (partState as? PartState.Loaded)?.let { onShareFile(it.partId) }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun onShareFile(partId: String) {
        viewModel.extractPart(partId, exportsDir) { file ->
            if (file != null) {
                val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.export", file)
                ShareCompat.IntentBuilder(this)
                        .setType("application/octet-stream")
                        .setStream(uri)
                        .startChooser()
            }
        }
    }

    private fun onExport(mimeTypes: List<MimeType>, x: Float? = null, y: Float? = null, selectedBlockId: String? = null) {
        if (mimeTypes.isNotEmpty()) {
            val label = mimeTypes.map { mimeType ->
                val extension = mimeType.primaryFileExtension
                // prepend `*` to display `*.jpeg`
                getString(R.string.editor_export_type_label, mimeType.getName(), "*$extension")
            }
            launchSingleChoiceDialog(R.string.editor_menu_export, label, 0) {
                val mimeType = mimeTypes[it]
                viewModel.exportContent(mimeType, x, y, selectedBlockId, exportsDir) { file ->
                    if (file != null) {
                        val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.export", file)
                        if (mimeType == MimeType.HTML || mimeType.isImage) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mimeType.typeName)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(intent, uri.lastPathSegment))
                        } else {
                            ShareCompat.IntentBuilder(this)
                                    .setType(mimeType.typeName)
                                    .setStream(uri)
                                    .startChooser()
                        }
                    } else {
                        Toast.makeText(this, R.string.editor_export_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun onPartCreationRequest(request: NewPartRequest?) {
        if (request != null) {
            val partTypes = request.availablePartTypes
            val defaultIndex = partTypes.indexOf(request.defaultPartType)
            launchSingleChoiceDialog(R.string.nav_new_part_dialog_title, partTypes.map(PartType::toString), defaultIndex) {
                viewModel.createPart(partTypes[it])
            }
        }
    }

    private fun onPartStateUpdate(state: PartState) {
        partState = state
        supportActionBar?.let {
            val (title, subtitle) = when (state.partId) {
                null -> getString(R.string.app_name) to null
                else -> (state.partType?.toString() ?: "…") to state.partId
            }
            it.title = title
            it.subtitle = subtitle
        }

        editorView?.isVisible = state.isReady

        binding.partEditorProgress.isVisible = state is PartState.Loading
        binding.editorToolbarSheet.toolbarSettingsBottomSheet.isVisible = state != PartState.Unloaded
        with(binding.editorToolbar) {
            partEditorControls.isVisible = state != PartState.Unloaded
            switchActivePen.isEnabled = state.isReady
            editorZoomIn.isEnabled = state.isReady
            editorZoomOut.isEnabled = state.isReady
            editorResetView.isEnabled = state.isReady
            editorClearContent.isEnabled = state.isReady
        }
    }

    private fun onPartHistoryUpdate(state: PartHistoryState) {
        with(binding.editorToolbar) {
            editorRedo.isEnabled = state.canRedo
            editorUndo.isEnabled = state.canUndo
        }
    }

    private fun onPartNavigationStateUpdate(state: PartNavigationState) {
        navigationState = state
        invalidateOptionsMenu()
    }

    private fun onPredictionSettingsRequest(request: NewPredictionRequest?) {
        if (request != null) {
            launchPredictionDialog(request.enabled, request.durationMs) { enable: Boolean, durationMs: Int ->
                viewModel.applyPredictionSettings(enable, durationMs)
            }
        }
    }
}
