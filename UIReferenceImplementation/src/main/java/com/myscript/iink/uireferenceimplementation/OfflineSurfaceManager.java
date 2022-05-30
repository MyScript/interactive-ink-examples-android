// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.graphics.Bitmap;
import android.util.SparseArray;

import androidx.annotation.Nullable;

public class OfflineSurfaceManager
{
  private int nextID = 0;

  private final SparseArray<Bitmap> offlineSurfaces = new SparseArray<>();

  public synchronized int create(int width, int height, boolean alphaOnly)
  {
    int offscreenID = nextID++;

    Bitmap surface;
    try
    {
      surface = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }
    catch (Exception | OutOfMemoryError e)
    {
      return -1;
    }
    offlineSurfaces.put(offscreenID, surface);

    return offscreenID;
  }

  public synchronized void release(int offscreenID)
  {
    if (offscreenID < 0)
      return;// not a correct id...
    Bitmap bitmap = offlineSurfaces.get(offscreenID);
    // can be null after a rotation (a new OfflineSurfaceManager can be created during animations)
    if (bitmap == null)
      return;
    bitmap.recycle();
    offlineSurfaces.remove(offscreenID);
  }

  @Nullable
  public synchronized Bitmap getBitmap(int id)
  {
    // can return null after a rotation (a new OfflineSurfaceManager can be created during animations)
    return offlineSurfaces.get(id);
  }
}
