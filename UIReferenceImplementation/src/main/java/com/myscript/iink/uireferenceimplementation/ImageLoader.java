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
  public interface Observer
  {
    void ready(String url, Bitmap image);
  }

  private Editor editor;
  private File cacheDirectory;
  LruCache<String, Bitmap> cache;

  public ImageLoader(Editor editor, File cacheDirectory)
  {
    this.editor = editor;
    this.cacheDirectory = new File(cacheDirectory, "tmp/render-cache");

    // Use a part of the maximum available memory to define the cache's size
    int cacheSize = (int) (Runtime.getRuntime().maxMemory() / 8);

    this.cache = new LruCache<String, Bitmap>(cacheSize)
    {
      protected int sizeOf(String key, Bitmap value)
      {
        return value.getByteCount();
      }
    };

    this.cacheDirectory.mkdirs();
  }

  public Editor getEditor()
  {
    return editor;
  }

  public File getCacheDirectory()
  {
    return cacheDirectory;
  }

  public synchronized Bitmap getImage(final String url, final String mimeType, final int dstWidth, final int dstHeight, final Observer observer)
  {
    Bitmap image = cache.get(url);

    if (image != null)
      return image;

    Thread thread = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        Bitmap image = renderObject(url, mimeType, dstWidth, dstHeight);

        if (image != null)
        {
          synchronized (ImageLoader.this)
          {
            cache.put(url, image);
          }
        }

        observer.ready(url, image);
      }
    });

    thread.start();
    return image;
  }

  private final Bitmap renderObject(String url, String mimeType, int dstWidth, int dstHeight)
  {
    if (mimeType.startsWith("image/"))
    {
      try
      {
        File file = getFile(url);
        Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath());

        file.delete();

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

  private final synchronized File getFile(String url) throws IOException
  {
    File file = new File(cacheDirectory, url);
    file.getParentFile().mkdirs();
    editor.getPart().getPackage().extractObject(url, file);
    return file;
  }
}
