// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import com.myscript.iink.Editor;
import com.myscript.iink.IRenderTarget;
import com.myscript.iink.IRenderTarget.LayerType;
import com.myscript.iink.Renderer;

import java.util.EnumSet;
import java.util.Map;

import androidx.annotation.Nullable;

public class LayerView extends View
{
  private final static int MODEL = 0;
  private final static int CAPTURE = 1;
  private ImageLoader imageLoader;

  @Nullable
  private Map<String, Typeface> typefaceMap;

  @Nullable
  private Renderer lastRenderer = null;

  @Nullable
  private Rect updateArea;
  @Nullable
  private Bitmap[] bitmap;
  @Nullable
  private android.graphics.Canvas[] sysCanvas;
  @Nullable
  private Canvas[] iinkCanvas;
  @Nullable
  private OfflineSurfaceManager offlineSurfaceManager = null;
  @Nullable
  private Renderer renderer = null;
  private int pageHeight = 0;
  private int viewHeight = 0;
  private int viewWidth = 0;
  private int pageWidth = 0;
  private int yMin = 0;
  private int xMin = 0;

  public LayerView(Context context)
  {
    this(context, null, 0);
  }

  public LayerView(Context context, @Nullable AttributeSet attrs)
  {
    this(context, attrs, 0);
  }

  public LayerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);

    updateArea = null;
  }

  public void setRenderTarget(IRenderTarget renderTarget)
  {
    // do not need the renderTarget
  }

  public void setOfflineSurfaceManager(@Nullable OfflineSurfaceManager offlineSurfaceManager)
  {
    this.offlineSurfaceManager = offlineSurfaceManager;
  }

  public void setEditor(Editor editor)
  {
    // do not need the editor
  }

  public void setImageLoader(ImageLoader imageLoader)
  {
    this.imageLoader = imageLoader;
  }

  public void setCustomTypefaces(Map<String, Typeface> typefaceMap)
  {
    this.typefaceMap = typefaceMap;
  }

  @Override
  protected final void onDraw(android.graphics.Canvas canvas)
  {
    Rect localUpdateArea;
    Renderer renderer;

    synchronized (this)
    {
      localUpdateArea = this.updateArea;

      this.updateArea = null;
      renderer = lastRenderer;
      lastRenderer = null;
    }

    if (localUpdateArea != null)
    {
      prepare(sysCanvas[MODEL], localUpdateArea);
      try
      {
        renderer.drawModel(localUpdateArea.left, localUpdateArea.top, localUpdateArea.width(), localUpdateArea.height(), iinkCanvas[MODEL]);
      }
      finally
      {
        restore(sysCanvas[MODEL]);
      }

      prepare(sysCanvas[CAPTURE], localUpdateArea);
      try
      {
        renderer.drawCaptureStrokes(localUpdateArea.left, localUpdateArea.top, localUpdateArea.width(), localUpdateArea.height(), iinkCanvas[CAPTURE]);
      }
      finally
      {
        restore(sysCanvas[CAPTURE]);
      }
    }

    canvas.drawBitmap(bitmap[MODEL], 0, 0, null);
    canvas.drawBitmap(bitmap[CAPTURE], 0, 0, null);
  }

  @Override
  protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight)
  {
    if (bitmap != null)
    {
      bitmap[MODEL].recycle();
      bitmap[CAPTURE].recycle();
    }
    DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();

    bitmap = new Bitmap[2];
    bitmap[MODEL] = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
    bitmap[CAPTURE] = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
    sysCanvas = new android.graphics.Canvas[2];
    sysCanvas[MODEL] = new android.graphics.Canvas(bitmap[MODEL]);
    sysCanvas[CAPTURE] = new android.graphics.Canvas(bitmap[CAPTURE]);
    iinkCanvas = new Canvas[2];
    iinkCanvas[MODEL] = new Canvas(sysCanvas[MODEL], typefaceMap, imageLoader, offlineSurfaceManager, metrics.xdpi, metrics.ydpi);
    iinkCanvas[CAPTURE] = new Canvas(sysCanvas[CAPTURE], typefaceMap, imageLoader, offlineSurfaceManager, metrics.xdpi, metrics.ydpi);

    super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
  }

  private void prepare(android.graphics.Canvas canvas, Rect clipRect)
  {
    canvas.save();
    canvas.clipRect(clipRect);
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
  }

  private void restore(android.graphics.Canvas canvas)
  {
    canvas.restore();
  }

  public final void update(Renderer renderer, int x, int y, int width, int height, EnumSet<LayerType> layers)
  {
    boolean emptyArea;
    synchronized (this)
    {
      if (updateArea != null)
        updateArea.union(x, y, x + width, y + height);
      else
        updateArea = new Rect(x, y, x + width, y + height);

      if (bitmap != null)
      {
        if (bitmap[MODEL] != null)
          updateArea.intersect(new Rect(0, 0, bitmap[MODEL].getWidth(), bitmap[MODEL].getHeight()));
        if (bitmap[CAPTURE] != null)
          updateArea.intersect(new Rect(0, 0, bitmap[CAPTURE].getWidth(), bitmap[CAPTURE].getHeight()));
      }
      emptyArea = updateArea.isEmpty();
      lastRenderer = renderer;
    }
    if (!emptyArea)
    {
      postInvalidate(x, y, x + width, y + height);
    }
  }

  public void setScrollbar(Renderer renderer, int viewWidthPx, int pageWidthtPx, int xMin, int viewHeightPx, int pageHeightPx, int yMin)
  {
    this.viewWidth = viewWidthPx;
    this.pageWidth = pageWidthtPx;
    this.renderer = renderer;
    this.pageHeight = pageHeightPx;
    this.viewHeight = viewHeightPx;
    this.xMin = xMin;
    this.yMin = yMin;
    setVerticalScrollBarEnabled(true);
    awakenScrollBars();
  }

  @Override
  protected int computeVerticalScrollRange()
  {
    return pageHeight;
  }

  @Override
  protected int computeVerticalScrollExtent()
  {
    return viewHeight;
  }

  @Override
  protected int computeVerticalScrollOffset()
  {
    return renderer != null ? (int) renderer.getViewOffset().y - yMin : 0;
  }

  @Override
  protected int computeHorizontalScrollRange()
  {
    return pageWidth;
  }

  @Override
  protected int computeHorizontalScrollExtent()
  {
    return viewWidth;
  }

  @Override
  protected int computeHorizontalScrollOffset()
  {
    return renderer != null ? (int) renderer.getViewOffset().x - xMin : 0;
  }
}
