// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.myscript.iink.IRenderTarget;
import com.myscript.iink.IRenderTarget.LayerType;
import com.myscript.iink.Renderer;

import java.util.Map;

public class LayerView extends View
{
  private LayerType type;
  private IRenderTarget renderTarget;

  private ImageLoader imageLoader;

  @Nullable
  private Map<String, Typeface> typefaceMap;

  @Nullable
  private Renderer lastRenderer = null;

  boolean updateLayer;
  @NonNull
  private RectF updateArea;
  private RectF updateArea_;
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

    updateLayer = false;
    updateArea = new RectF();
    updateArea_ = new RectF();

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

  public void setImageLoader(ImageLoader imageLoader)
  {
    this.imageLoader = imageLoader;
  }

  public void setCustomTypefaces(Map<String, Typeface> typefaceMap)
  {
    this.typefaceMap = typefaceMap;
  }

  public void setRenderTarget(IRenderTarget renderTarget)
  {
    this.renderTarget = renderTarget;
  }

  public LayerType getType()
  {
    return type;
  }

  @Override
  protected final void onDraw(android.graphics.Canvas canvas)
  {
    boolean updateLayer_;
    Renderer renderer;

    synchronized (this)
    {
      updateLayer_ = updateLayer;
      if (updateLayer_)
        updateArea_.union(updateArea);
      renderer = lastRenderer;
      updateArea.setEmpty();
      updateLayer = false;
      lastRenderer = null;
    }

    if (updateLayer_)
    {
      Rect rect_ = new Rect((int)updateArea_.left, (int)updateArea_.top, (int)updateArea_.right, (int)updateArea_.bottom);
      updateArea_.setEmpty();

      prepare(sysCanvas, rect_);
      try
      {
        switch (type)
        {
          case BACKGROUND:
          {
            renderer.drawBackground(rect_.left, rect_.top, rect_.width(), rect_.height(), iinkCanvas);
            break;
          }
          case MODEL:
          {
            renderer.drawModel(rect_.left, rect_.top, rect_.width(), rect_.height(), iinkCanvas);
            break;
          }
          case TEMPORARY:
          {
            renderer.drawTemporaryItems(rect_.left, rect_.top, rect_.width(), rect_.height(), iinkCanvas);
            break;
          }
          case CAPTURE:
          {
            renderer.drawCaptureStrokes(rect_.left, rect_.top, rect_.width(), rect_.height(), iinkCanvas);
            break;
          }
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

  public final void update(Renderer renderer, int x, int y, int width, int height)
  {
    synchronized (this)
    {
      updateLayer = true;
      updateArea.union(x, y);
      updateArea.union(x + width, y + height);

      lastRenderer = renderer;
    }

    postInvalidate(x, y, x + width, y + height);
  }
}
