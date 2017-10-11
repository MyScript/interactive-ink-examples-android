// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import com.myscript.iink.graphics.IPath;

import java.util.EnumSet;

public class Path extends android.graphics.Path implements IPath
{

  @Override
  public EnumSet<OperationType> unsupportedOperations()
  {
    return EnumSet.of(OperationType.ARC_OPS);
  }

  @Override
  public void curveTo(float x1, float y1, float x2, float y2, float x, float y)
  {
    cubicTo(x1, y1, x2, y2, x, y);
  }

  @Override
  public void arcTo(float rx, float ry, float phi, boolean fA, boolean fS, float x, float y)
  {
    throw new UnsupportedOperationException("arcTo");
  }

  @Override
  public void closePath()
  {
    close();
  }

}
