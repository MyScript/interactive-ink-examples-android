// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumSet;

import android.graphics.Bitmap;

import com.myscript.iink.IImageDrawer;
import com.myscript.iink.Renderer;

public class ImageDrawer implements IImageDrawer
{
  private ImageLoader imageLoader = null;
  private Bitmap              bitmap      = null;
  private android.graphics.Canvas canvas      = null;

  public void setImageLoader(ImageLoader imageLoader)
  {
    this.imageLoader = imageLoader;
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

    canvas.drawARGB(255, 255, 255, 255);
    Canvas androidCanvas = new Canvas(canvas, null, imageLoader, this);

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
      Bitmap.CompressFormat format = Bitmap.CompressFormat.PNG;

      if (path.endsWith(".png"))
        format = Bitmap.CompressFormat.PNG;
      else if (path.endsWith(".jpg"))
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

    if (bitmap != null)
    {
      bitmap.recycle();
      bitmap = null;
    }
    canvas = null;
  }
}
