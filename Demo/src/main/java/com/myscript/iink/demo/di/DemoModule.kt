// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.di

import android.app.Application
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.myscript.certificate.MyCertificate
import com.myscript.iink.Engine
import com.myscript.iink.demo.IInkApplication
import com.myscript.iink.demo.R
import com.myscript.iink.demo.data.ContentRepository
import com.myscript.iink.demo.data.ToolRepository
import com.myscript.iink.demo.domain.PartEditor
import com.myscript.iink.demo.domain.ToolType
import com.myscript.iink.demo.domain.storageKey
import com.myscript.iink.demo.ui.ColorPalette
import com.myscript.iink.demo.ui.EditorViewModel
import com.myscript.iink.demo.ui.Thickness
import com.myscript.iink.demo.ui.opaque
import com.myscript.iink.demo.ui.toFloat
import com.myscript.iink.uireferenceimplementation.Canvas
import com.myscript.iink.uireferenceimplementation.EditorBinding
import com.myscript.iink.uireferenceimplementation.FontUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

class DemoModule(application: Application) {

    val engine: Engine?
    val colorPalette: ColorPalette
    val defaultTypeface: Typeface
    val extraBrushes: List<Canvas.ExtraBrushConfig>
    val editor: PartEditor
    val editorBinding: EditorBinding

    init {
        engine = try {
            provideEngine(application, MyCertificate.getBytes())
        } catch (e: Exception) {
            null
        }

        colorPalette = provideColorPalette()
        val typefaces = provideTypefaces(application)
        defaultTypeface = provideDefaultTypeface(application)
        extraBrushes = provideExtraBrushConfigurations(application, engine)
        val preferences = providePreferences(application)
        val editorTheme = provideEditorTheme(application)
        editor = PartEditor(
            typefaces,
            editorTheme,
            providePartRepository(application, engine, preferences),
            provideToolRepository(preferences, colorPalette),
            extraBrushes
        )
        editorBinding = EditorBinding(engine, typefaces)
    }

    private fun providePreferences(application: Application): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(application)
    }

    private fun provideTypefaces(application: Application): Map<String, Typeface> {
        val typefaces = FontUtils.loadFontsFromAssets(application.assets) ?: mutableMapOf()
        // Map key must be aligned with the font-family used in theme.css
        val myscriptInterFont = ResourcesCompat.getFont(application, R.font.myscriptinter)
        if (myscriptInterFont != null) {
            typefaces["MyScriptInter"] = myscriptInterFont
        }
        val stixFont = ResourcesCompat.getFont(application, R.font.stix)
        if (stixFont != null) {
            typefaces["STIX"] = stixFont
        }
        return typefaces
    }

    private fun provideDefaultTypeface(application: Application): Typeface {
        return ResourcesCompat.getFont(application, R.font.myscriptinter) ?: Typeface.SANS_SERIF
    }

    private fun provideEditorTheme(application: Application): String {
        application.resources.openRawResource(R.raw.theme).use { input ->
            ByteArrayOutputStream().use { output ->
                input.copyTo(output)
                return output.toString(StandardCharsets.UTF_8.name())
            }
        }
    }

    private fun provideEngine(application: Application, certificate: ByteArray): Engine {
        return Engine.create(certificate).apply {
            // configure recognition
            configuration.let { conf ->
                val confDir = "zip://${application.packageCodePath}!/assets/conf"
                conf.setStringArray("configuration-manager.search-path", arrayOf(confDir))
                val tempDir = File(application.cacheDir, "tmp")
                conf.setString("content-package.temp-folder", tempDir.absolutePath)
            }
        }
    }

    private fun providePartRepository(application: Application, engine: Engine?, preferences: SharedPreferences): ContentRepository {
        return ContentRepository(File(application.filesDir, "data"), engine, preferences, application.assets)
    }

    private fun provideToolRepository(preferences: SharedPreferences, colorPalette: ColorPalette): ToolRepository {
        val toolRepository = ToolRepository(preferences)
        with(ToolType.PEN) {
            val color = toolRepository.getToolColor(storageKey)
            if (color == Color.TRANSPARENT) {
                val defaultColor = colorPalette.getColors(this).firstOrNull() ?: return@with
                toolRepository.saveToolColor(storageKey, defaultColor)
            }
            val thickness = toolRepository.getToolThickness(storageKey)
            if (thickness == 0f) {
                toolRepository.saveToolThickness(storageKey, Thickness.MEDIUM.toFloat(this))
            }
        }
        with(ToolType.HIGHLIGHTER) {
            val color = toolRepository.getToolColor(storageKey)
            if (color == Color.TRANSPARENT) {
                val defaultColor = colorPalette.getColors(this).firstOrNull() ?: return@with
                toolRepository.saveToolColor(storageKey, defaultColor)
            }
            val thickness = toolRepository.getToolThickness(storageKey)
            if (thickness == 0f) {
                toolRepository.saveToolThickness(storageKey, Thickness.MEDIUM.toFloat(this))
            }
        }
        return toolRepository
    }

    private fun provideColorPalette(): ColorPalette {
        // This is the built-in color palette.
        // One could envisage to tweak it depending on dark/light.
        // It could come from remote user preferences or stored preferences.
        // All colors could be shared by all tools and not being specific to such tools.
        // There might be a single color available, so this become useless.
        // Depending on application design, the way such palette is built and provided might evolve.
        return ColorPalette(mapOf(
                ToolType.PEN to listOf(
                        0x000000.opaque,
                        0xEA4335.opaque,
                        0x34A853.opaque,
                        0x4285F4.opaque,
                ),
                ToolType.HIGHLIGHTER to listOf(
                        0xFBBC05.opaque,
                        0xEA4335.opaque,
                        0x34A853.opaque,
                        0x4285F4.opaque,
                )
        ))
    }

    private fun provideExtraBrushConfigurations(
        application: Application,
        engine: Engine?
    ): List<Canvas.ExtraBrushConfig> {
        val e = engine ?: return emptyList()

        val options = BitmapFactory.Options().apply {
            inScaled = false
        }

        // configure Pencil
        val stampBitmap = BitmapFactory.decodeResource(
            application.resources,
            R.drawable.texture_stamp,
            options
        )
        val backgroundBitmap = BitmapFactory.decodeResource(
            application.resources,
            R.drawable.texture_background,
            options
        )
        val parameters = e.createParameterSet().apply {
            setString("draw-method", "stamp-reveal")
            setBoolean("mirror-background", false)
            setNumber("stamp-original-orientation", -Math.PI / 4.0) // pointing up-left
            setNumber("stamp-min-distance", 0.3)
            setNumber("stamp-max-distance", 0.5)
            setNumber("default-pressure-sensitivity", 1.0) // forced if none/zero from styling
            setNumber("scale-min-pressure", 1.1)
            setNumber("scale-max-pressure", 1.3) // might reach internal saturation
            setNumber("opacity-min-pressure", 0.1)
            setNumber("opacity-max-pressure", 0.25)
            setNumber("amortized-pressure-factor", 1.5)
            setNumber("tilt-max-angle-threshold", 1.05) // radians
            setNumber("tilt-flat-angle-threshold", 0.75)
            setNumber("tilt-tip-angle-threshold", 0.2)
            setNumber("tilt-flat-scale-factor", 4.5) // might reach internal saturation
            setNumber("tilt-tip-scale-factor", 0.5)
            setNumber("tilt-flat-opacity-factor", 0.25)
            setNumber("tilt-tip-opacity-factor", 5.0)
        }
        val pencilBrushConfig = Canvas.ExtraBrushConfig(
            "${EXTRA_BRUSH_PREFIX}Pencil",
            stampBitmap,
            backgroundBitmap,
            parameters
        )
        return listOf(pencilBrushConfig)
    }

    fun close() {
        engine?.close()
    }

    companion object {
        // All extra brushes names must start with this prefix
        private const val EXTRA_BRUSH_PREFIX = "Extra-"
    }
}

class EditorViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(EditorViewModel::class.java) -> {
                EditorViewModel(IInkApplication.DemoModule.editor, IInkApplication.DemoModule.colorPalette) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel $modelClass")
        }
    }
}
