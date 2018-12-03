// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.myscript.iink.Editor;
import com.myscript.iink.IRenderTarget;
import com.myscript.iink.IRenderTarget.LayerType;
import com.myscript.iink.Renderer;

import java.util.EnumSet;
import java.util.Map;

public class LayerView extends View implements IRenderView
{
  private LayerType type;
  private IRenderTarget renderTarget;

  private ImageLoader imageLoader;

  @Nullable
  private Map<String, Typeface> typefaceMap;

  @Nullable
  private Renderer lastRenderer = null;

  @Nullable
  private Rect updateArea;
  @Nullable
  private Bitmap bitmap;
  @Nullable
  private android.graphics.Canvas sysCanvas;
  @Nullable
  private Canvas iinkCanvas;

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

    TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.LayerView, defStyleAttr, 0);
    try
    {
      int typeOrdinal = typedArray.getInteger(R.styleable.LayerView_layerType, 0);
      type = LayerType.values()[typeOrdinal];
    }
    finally
    {
      typedArray.recycle();
    }
  }

  @Override
  public boolean isSingleLayerView()
  {
    return true;
  }

  @Override
  public LayerType getType()
  {
    return type;
  }

  @Override
  public void setRenderTarget(IRenderTarget renderTarget)
  {
    this.renderTarget = renderTarget;
  }

  @Override
  public void setEditor(Editor editor)
  {
    // don't need the editor
  }

  @Override
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

      prepare(sysCanvas, localUpdateArea);
      try
      {
        switch (type)
        {
          case BACKGROUND:
            renderer.drawBackground(localUpdateArea.left, localUpdateArea.top, localUpdateArea.width(), localUpdateArea.height(), iinkCanvas);
            break;
          case MODEL:
            renderer.drawModel(localUpdateArea.left, localUpdateArea.top, localUpdateArea.width(), localUpdateArea.height(), iinkCanvas);
            break;
          case TEMPORARY:
            renderer.drawTemporaryItems(localUpdateArea.left, localUpdateArea.top, localUpdateArea.width(), localUpdateArea.height(), iinkCanvas);
            break;
          case CAPTURE:
            renderer.drawCaptureStrokes(localUpdateArea.left, localUpdateArea.top, localUpdateArea.width(), localUpdateArea.height(), iinkCanvas);
            break;
          default:
            // unknown layer type
            break;
        }
      }
      finally
      {
        restore(sysCanvas);
      }
    }

    canvas.drawBitmap(bitmap, 0, 0, null);
  }

  @Override
  protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight)
  {
    if (bitmap != null)
    {
      bitmap.recycle();
    }
    bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
    sysCanvas = new android.graphics.Canvas(bitmap);
    iinkCanvas = new Canvas(sysCanvas, typefaceMap, imageLoader, renderTarget);

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

  @Override
  public final void update(Renderer renderer, int x, int y, int width, int height, EnumSet<LayerType> layers)
  {
    synchronized (this)
    {
      if (updateArea != null)
        updateArea.union(x, y, x + width, y + height);
      else
        updateArea = new Rect(x, y, x + width, y + height);

      lastRenderer = renderer;
    }

    postInvalidate(x, y, x + width, y + height);
  }
}
