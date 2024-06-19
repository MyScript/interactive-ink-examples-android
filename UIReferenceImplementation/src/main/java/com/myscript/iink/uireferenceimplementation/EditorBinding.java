// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.util.DisplayMetrics;

import com.myscript.iink.Editor;
import com.myscript.iink.Engine;
import com.myscript.iink.ParameterSet;
import com.myscript.iink.Renderer;

import java.util.ArrayList;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class EditorBinding
{
  @Nullable
  private final Engine engine;
  @NonNull
  private final Map<String, Typeface> typefaces;
  @Nullable
  private InputController inputController;
  @Nullable
  ArrayList<Canvas.ExtraBrushConfig> extraBrushConfigs;

  public EditorBinding(@Nullable Engine engine, @NonNull Map<String, Typeface> typefaces)
  {
    this.engine = engine;
    this.typefaces = typefaces;
  }

  private void bindEditor(@NonNull EditorView editorView, @Nullable Editor editor)
  {
    editorView.setTypefaces(typefaces);
    editorView.setExtraBrushConfigs(extraBrushConfigs);
    editorView.setEditor(editor);
    if (editor != null)
    {
      editorView.setImageLoader(new ImageLoader(editor));
      inputController = new InputController(editorView.getContext(), editorView, editor);
    }
    else
    {
      editorView.setImageLoader(null);
      inputController = null;
    }
    editorView.setOnTouchListener(inputController);
  }

  private void configureExtraBrushes(@NonNull EditorView editorView, @NonNull Editor editor)
  {
    if (editor == null)
    {
      extraBrushConfigs = null;
      return;
    }

    //Note: all extra brushes names must start with this prefix
    final String ExtraBrushPrefix = "Extra-";

    extraBrushConfigs = new ArrayList<>();
    BitmapFactory.Options opt = new BitmapFactory.Options();
    opt.inScaled = false;
    {
      // configure Pencil
      Bitmap stampBitmap = BitmapFactory.decodeResource(editorView.getResources(), R.drawable.texture_stamp, opt);
      Bitmap backgroundBitmap = BitmapFactory.decodeResource(editorView.getResources(), R.drawable.texture_background, opt);
      ParameterSet config = editor.getEngine().createParameterSet();
      config.setString("draw-method", "stamp-reveal");
      config.setBoolean("mirror-background", true);
      config.setNumber("stamp-min-distance", 0.3);
      config.setNumber("stamp-max-distance", 0.5);

      // optional config
      config.setNumber("scale-min-pressure", 0.33);
      config.setNumber("scale-max-pressure", 1.0);
      config.setNumber("opacity-min-pressure", 0.075);
      config.setNumber("opacity-max-pressure", 1.0);
      config.setNumber("amortized-pressure-factor", 1.5);
      config.setNumber("point-min-opacity", 0.5);
      config.setNumber("background-forced-opacity", 0.67);

      extraBrushConfigs.add(new Canvas.ExtraBrushConfig(ExtraBrushPrefix + "Pencil", stampBitmap, backgroundBitmap, config));
    }
  }

  @NonNull
  public final EditorData openEditor(@Nullable EditorView editorView)
  {
    Editor editor = null;
    Renderer renderer = null;
    if (engine != null && editorView != null)
    {
      Resources resources = editorView.getResources();
      DisplayMetrics displayMetrics = resources.getDisplayMetrics();
      renderer = engine.createRenderer(displayMetrics.xdpi, displayMetrics.ydpi, editorView);
      renderer.setViewOffset(0.0f, 0.0f);
      renderer.setViewScale(1.0f);
      editor = engine.createEditor(renderer, engine.createToolController());
      editor.setFontMetricsProvider(new FontMetricsProvider(displayMetrics, typefaces));
      configureExtraBrushes(editorView, editor);
      bindEditor(editorView, editor);
    }
    return new EditorData(editor, renderer, inputController, extraBrushConfigs);
  }
}
