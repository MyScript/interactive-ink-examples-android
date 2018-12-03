// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.annotation.ColorInt;

import com.myscript.iink.IImageDrawer;
import com.myscript.iink.Renderer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;

public class ImageDrawer implements IImageDrawer
{
  private ImageLoader imageLoader = null;
  private Map<String, Typeface> typefaceMap = null;
  private Bitmap bitmap = null;
  private android.graphics.Canvas canvas = null;
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
  public void invalidate(Renderer renderer, EnumSet<LayerType> layers)
  {
    if (canvas == null)
      return;

    int width = canvas.getWidth();
    int height = canvas.getHeight();
    invalidate(renderer, 0, 0, width, height, layers);
  }

  @Override
  public void invalidate(Renderer renderer, int x, int y, int width, int height, EnumSet<LayerType> layers)
  {
    if (canvas == null)
      return;

    canvas.drawARGB(Color.alpha(backgroundColor), Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor));
    Canvas androidCanvas = new Canvas(canvas, typefaceMap, imageLoader, this);

    if (layers.contains(LayerType.MODEL))
      renderer.drawModel(x, y, width, height, androidCanvas);
    if (layers.contains(LayerType.TEMPORARY))
      renderer.drawTemporaryItems(x, y, width, height, androidCanvas);
    if (layers.contains(LayerType.CAPTURE))
      renderer.drawCaptureStrokes(x, y, width, height, androidCanvas);
  }

  @Override
  public void prepareImage(int width, int height)
  {
    if (bitmap != null)
    {
      bitmap.recycle();
      bitmap = null;
    }
    canvas = null;

    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    canvas = new android.graphics.Canvas(bitmap);
  }

  @Override
  public void saveImage(String path) throws IOException
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

      FileOutputStream stream = new FileOutputStream(path);
      bitmap.compress(format, quality, stream);
      stream.close();
    }
    catch (Exception e)
    {
      throw new IOException("Can't save image");
    }

    bitmap.recycle();
    bitmap = null;
    canvas = null;
  }
}
