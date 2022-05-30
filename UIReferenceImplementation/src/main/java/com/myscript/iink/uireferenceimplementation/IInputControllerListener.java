// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import androidx.annotation.Nullable;

public interface IInputControllerListener
{
  boolean onLongPress(final float x, final float y, final @Nullable String contentBlockId);
}
