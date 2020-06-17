// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.LruCache;

import com.myscript.iink.Editor;

import java.io.File;
import java.io.IOException;

public class ImageLoader
{
  private Editor editor;
  LruCache<String, Bitmap> cache;

  public ImageLoader(Editor editor)
  {
    this.editor = editor;

    // Use a part of the maximum available memory to define the cache's size
    int cacheSize = (int) (Runtime.getRuntime().maxMemory() / 8);

    this.cache = new LruCache<String, Bitmap>(cacheSize)
    {
      protected int sizeOf(String key, Bitmap value)
      {
        return value.getByteCount();
      }
    };
  }

  public Editor getEditor()
  {
    return editor;
  }

  public synchronized Bitmap getImage(final String url, final String mimeType, final int dstWidth, final int dstHeight)
  {
    Bitmap image = cache.get(url);
    if (image != null)
      return image;

    image = renderObject(url, mimeType, dstWidth, dstHeight);
    cache.put(url, image);

    return image;
  }

  private final Bitmap renderObject(String url, String mimeType, int dstWidth, int dstHeight)
  {
    if (mimeType.startsWith("image/"))
    {
      try
      {
        File file = new File(url);
        Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath());

        if (image != null)
        {
          Bitmap scaledImage = Bitmap.createScaledBitmap(image, dstWidth, dstHeight, false);

          if (scaledImage != null)
            return scaledImage;
        }
      }
      catch (Exception e)
      {
        // Error: use fallback bitmap
      }
    }

    // Fallback 1x1 bitmap
    Bitmap image = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
    if (image != null)
      image.eraseColor(Color.WHITE);
    return image;
  }
}
