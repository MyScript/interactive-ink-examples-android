// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.graphics.Bitmap;
import android.util.SparseArray;

import java.util.LinkedList;
import java.util.Queue;

public class OfflineSurfaceManager
{
  private int nextID = 0;

  private final SparseArray<Bitmap> offlineSurfaces = new SparseArray<>();

  public OfflineSurfaceManager()
  {
  }

  public synchronized int create(int width, int height, boolean alphaOnly)
  {
    int offscreenID = nextID++;

    //first look for best fitting, previously allocated surface:
    int sizeArea = width * height;

    int bestId = -1;
    int bestDiff = Integer.MAX_VALUE;
    Bitmap surface;
    try
    {
      surface = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }
    catch (Exception e)
    {
      return -1;
    }
    catch (OutOfMemoryError e)
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
    offlineSurfaces.remove(offscreenID);
  }

  public synchronized Bitmap getBitmap(int id)
  {
    Bitmap bitmap = offlineSurfaces.get(id);
    // can return null after a rotation (a new OfflineSurfaceManager can be created during animations)
    return bitmap;
  }

  public void release()
  {
    for (int i = 0; i < offlineSurfaces.size(); i++)
    {
      int key = offlineSurfaces.keyAt(i);
      // get the object by the key.
      Bitmap bitmap = offlineSurfaces.get(key);
      bitmap.recycle();
    }
    offlineSurfaces.clear();
  }
}
