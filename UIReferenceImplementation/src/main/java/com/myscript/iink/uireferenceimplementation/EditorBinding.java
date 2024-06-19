// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.DisplayMetrics;

import com.myscript.iink.Editor;
import com.myscript.iink.Engine;
import com.myscript.iink.Renderer;

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

  public EditorBinding(@Nullable Engine engine, @NonNull Map<String, Typeface> typefaces)
  {
    this.engine = engine;
    this.typefaces = typefaces;
  }

  private void bindEditor(@NonNull EditorView editorView, @Nullable Editor editor)
  {
    editorView.setTypefaces(typefaces);
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

  @NonNull
  public EditorData openEditor(@Nullable EditorView editorView)
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
      bindEditor(editorView, editor);
    }
    return new EditorData(editor, renderer, inputController);
  }
}
