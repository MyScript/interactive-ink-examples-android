// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.Context;
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
  private OfflineSurfaceManager offlineSurfaceManager = null;
  @Nullable
  private Renderer renderer = null;
  @Nullable
  private Canvas iinkCanvas = null;
  private int pageWidth = 0;
  private int pageHeight = 0;
  private int viewWidth = 0;
  private int viewHeight = 0;
  private int canvasWidth = 0;
  private int canvasHeight = 0;
  private int xMin = 0;
  private int yMin = 0;

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
    super.onDraw(canvas);

    Rect updateArea;
    Renderer renderer;
    synchronized (this)
    {
      updateArea = new Rect(0, 0, canvasWidth, canvasHeight);
      renderer = lastRenderer;
    }

    iinkCanvas.setCanvas(canvas);
    prepare(canvas, updateArea);

    try
    {
      renderer.drawModel(updateArea.left, updateArea.top, updateArea.width(), updateArea.height(), iinkCanvas);
      renderer.drawCaptureStrokes(updateArea.left, updateArea.top, updateArea.width(), updateArea.height(), iinkCanvas);
    }
    finally
    {
      restore(canvas);
    }
  }

  @Override
  protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight)
  {
    DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();

    synchronized (this)
    {
      iinkCanvas = new Canvas(null, typefaceMap, imageLoader, offlineSurfaceManager, metrics.xdpi, metrics.ydpi);
      iinkCanvas.setClearOnStartDraw(false);
      canvasWidth = newWidth;
      canvasHeight = newHeight;
    }

    super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
  }

  private void prepare(android.graphics.Canvas canvas, Rect clipRect)
  {
    canvas.save();
    canvas.clipRect(clipRect);
  }

  private void restore(android.graphics.Canvas canvas)
  {
    canvas.restore();
  }

  public final void update(Renderer renderer, int x, int y, int width, int height, EnumSet<LayerType> layers)
  {
    boolean emptyArea;
    Rect updateArea = new Rect(x, y, x + width, y + height);
    synchronized (this)
    {
      if (canvasWidth > 0 && canvasHeight > 0)
        updateArea.intersect(new Rect(0, 0, canvasWidth, canvasHeight));

      emptyArea = updateArea.isEmpty();
      lastRenderer = renderer;
    }
    if (!emptyArea)
    {
      postInvalidate(x, y, x + width, y + height);
    }
  }

  public void setScrollbar(Renderer renderer, int viewWidthPx, int pageWidthPx, int xMin, int viewHeightPx, int pageHeightPx, int yMin)
  {
    this.viewWidth = viewWidthPx;
    this.pageWidth = pageWidthPx;
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
