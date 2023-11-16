// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;
import android.util.Pair;
import android.util.LruCache;

import java.io.File;

import com.myscript.iink.Editor;

import androidx.annotation.NonNull;


public class ImageLoader
{
  @NonNull
  private final Editor editor;
  LruCache<String, Bitmap> cache;
  static final float CACHE_MAX_MEMORY_RATIO = 1.f / 8; // in ]0, 1[

  public ImageLoader(@NonNull Editor editor)
  {
    this.editor = editor;

    // Use a part of the maximum available memory to define the cache's size (in Bytes)
    int cacheSize = (int) (Runtime.getRuntime().maxMemory() * CACHE_MAX_MEMORY_RATIO);

    this.cache = new LruCache<String, Bitmap>(cacheSize)
    {
      @Override
      protected int sizeOf(String key, Bitmap value)
      {
        // The cache size will be measured in bytes rather than number of items
        return value.getByteCount();
      }
      @Override
      protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue)
      {
        if (evicted && oldValue != null && !oldValue.isRecycled())
        {
          oldValue.recycle();
        }
        super.entryRemoved(evicted, key, oldValue, newValue);
      }
    };
  }

  @NonNull
  public Editor getEditor()
  {
    return editor;
  }

  public synchronized Bitmap getImage(final String url, final String mimeType, final int dstWidth, final int dstHeight)
  {
    Bitmap image = cache.get(url);
    if (image != null)
      return image; // found

    Pair<Bitmap, Boolean> newImage = renderObject(url, mimeType, dstWidth, dstHeight);

    if (newImage.second) // Not dummy
    {
      int imageSize = newImage.first.getByteCount();
      if (imageSize > cache.maxSize())
      {
        Log.w("ImageLoader", "Image too big for cache: resizing cache ("
            + imageSize / (1024.f * 1024.f) + "MB > " + cache.maxSize() / (1024.f * 1024.f) + "MB)");
        cache.resize(imageSize);
      }

      cache.put(url, newImage.first);
    }

    return newImage.first;
  }

  private Pair<Bitmap, Boolean> renderObject(String url, String mimeType, int dstWidth, int dstHeight)
  {
    if (mimeType.startsWith("image/"))
    {
      try
      {
        File file = new File(url);
        Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath());

        if (image != null)
        {
          // Reduce size if larger than destination
          if (image.getWidth() > dstWidth || image.getHeight() > dstHeight)
          {
            Bitmap scaledImage = Bitmap.createScaledBitmap(image, dstWidth, dstHeight, false);

            if (scaledImage != null)
              return Pair.create(scaledImage, true);
            else
              Log.e("ImageLoader", "Unable to scale image: using placeholder image");
          }
          else
          {
            return Pair.create(image, true);
          }
        }
        else
        {
          Log.e("ImageLoader", "Unable to decode file: using placeholder image");
        }
      }
      catch (Exception e)
      {
        // Error: use fallback bitmap
        Log.e("ImageLoader", "Unexpected exception: using placeholder image", e);
      }
      catch (OutOfMemoryError e)
      {
        // Error: use fallback bitmap
        Log.w("ImageLoader", "Out of memory: unable to load image: using placeholder instead", e);
      }
    }

    // Fallback 1x1 bitmap
    Bitmap image = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
    if (image != null)
      image.eraseColor(Color.WHITE);
    else
      Log.e("ImageLoader", "Unable to render image nor placeholder");

    return Pair.create(image, false);
  }
}
