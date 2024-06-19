// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;

import com.myscript.iink.IImagePainter;
import com.myscript.iink.graphics.ICanvas;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

public class ImagePainter implements IImagePainter
{
  private ImageLoader imageLoader = null;
  private Map<String, Typeface> typefaceMap = null;
  protected android.graphics.Canvas canvas = null;
  private Bitmap bitmap = null;
  @NonNull
  private final List<Canvas.ExtraBrushConfig> extraBrushConfigs;
  private float dpi = 96.f;
  @ColorInt
  private int backgroundColor = Color.WHITE;

  public ImagePainter()
  {
    this(Collections.emptyList());
  }

  public ImagePainter(@NonNull List<Canvas.ExtraBrushConfig> extraBrushConfigs)
  {
    this.extraBrushConfigs = extraBrushConfigs;
  }

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
    return new Canvas(canvas, extraBrushConfigs, typefaceMap, imageLoader, dpi, dpi);
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
