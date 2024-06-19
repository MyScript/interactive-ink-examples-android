// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import androidx.annotation.Nullable;

import com.myscript.iink.Editor;
import com.myscript.iink.Renderer;

import java.util.ArrayList;

public final class EditorData
{
  @Nullable
  private final Editor editor;
  @Nullable
  private final Renderer renderer;
  @Nullable
  private final InputController inputController;
  @Nullable
  private final ArrayList<Canvas.ExtraBrushConfig> extraBrushConfigs;

  @Nullable
  public final Editor getEditor()
  {
    return this.editor;
  }

  @Nullable
  public final Renderer getRenderer()
  {
    return this.renderer;
  }

  @Nullable
  public final InputController getInputController()
  {
    return this.inputController;
  }

  @Nullable
  public final ArrayList<Canvas.ExtraBrushConfig> getExtraBrushConfigs()
  {
    return this.extraBrushConfigs;
  }

  public EditorData(@Nullable Editor editor, @Nullable Renderer renderer, @Nullable InputController inputController,
                    @Nullable ArrayList<Canvas.ExtraBrushConfig> extraBrushConfigs)
  {
    this.editor = editor;
    this.renderer = renderer;
    this.inputController = inputController;
    this.extraBrushConfigs = extraBrushConfigs;
  }
}
