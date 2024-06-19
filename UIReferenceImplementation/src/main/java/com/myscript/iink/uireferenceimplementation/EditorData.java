// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import com.myscript.iink.Editor;
import com.myscript.iink.Renderer;

import androidx.annotation.Nullable;

public final class EditorData
{
  @Nullable
  private final Editor editor;
  @Nullable
  private final Renderer renderer;
  @Nullable
  private final InputController inputController;

  @Nullable
  public Editor getEditor()
  {
    return this.editor;
  }

  @Nullable
  public Renderer getRenderer()
  {
    return this.renderer;
  }

  @Nullable
  public InputController getInputController()
  {
    return this.inputController;
  }

  public EditorData(@Nullable Editor editor, @Nullable Renderer renderer, @Nullable InputController inputController)
  {
    this.editor = editor;
    this.renderer = renderer;
    this.inputController = inputController;
  }
}
