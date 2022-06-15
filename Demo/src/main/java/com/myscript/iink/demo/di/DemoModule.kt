// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.di

import android.app.Application
import android.content.SharedPreferences
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
import com.myscript.iink.uireferenceimplementation.EditorBinding
import com.myscript.iink.uireferenceimplementation.FontUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class DemoModule(application: Application) {

    val engine: Engine?
    val colorPalette: ColorPalette
    val defaultTypeface: Typeface
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
        val preferences = providePreferences(application)
        val editorTheme = provideEditorTheme(application)
        editor = PartEditor(typefaces, editorTheme, providePartRepository(application, engine, preferences), provideToolRepository(preferences, colorPalette))
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
        return ContentRepository(File(application.filesDir, "data"), engine, preferences)
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

    fun close() {
        engine?.close()
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
