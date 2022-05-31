// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.myscript.iink.IImagePainter;
import com.myscript.iink.graphics.ICanvas;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class ImagePainter implements IImagePainter
{
  private ImageLoader imageLoader = null;
  private Map<String, Typeface> typefaceMap = null;
  private Bitmap bitmap = null;
  private android.graphics.Canvas canvas = null;
  private float dpi = 96;

  @ColorInt
  private int backgroundColor = Color.WHITE;

  public void setImageLoader(ImageLoader imageLoader)
  {
    this.imageLoader = imageLoader;
  }

  public void setTypefaceMap(Map<String, Typeface> typefaceMap)
  {
    this.typefaceMap = typefaceMap;
  }

  public void setBackgroundColor(@ColorInt int backgroundColor)
  {
    this.backgroundColor = backgroundColor;
  }

  @Override
  public ICanvas createCanvas()
  {
    return new Canvas(canvas, typefaceMap, imageLoader, null, dpi, dpi);
  }

  @Override
  public void prepareImage(int width, int height, float dpi)
  {
    if (bitmap != null)
    {
      bitmap.recycle();
      bitmap = null;
    }
    canvas = null;

    this.dpi = dpi;
    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    canvas = new android.graphics.Canvas(bitmap);
    canvas.drawARGB(Color.alpha(backgroundColor), Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor));
  }

  @Override
  public void saveImage(@NonNull String path) throws IOException
  {
    if (bitmap == null)
      return;

    try
    {
      int quality = 100; // max quality
      Bitmap.CompressFormat format;

      if (path.endsWith(".png"))
        format = Bitmap.CompressFormat.PNG;
      else if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".jpe"))
        format = Bitmap.CompressFormat.JPEG;
      else
        throw new IOException("No appropriate image format found");

      try (OutputStream stream = new FileOutputStream(path))
      {
        bitmap.compress(format, quality, stream);
      }
    }
    catch (Exception e)
    {
      throw new IOException("Cannot save image");
    }
    finally
    {
      bitmap.recycle();
    }

    bitmap = null;
    canvas = null;
  }
}
