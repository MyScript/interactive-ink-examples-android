// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.myscript.iink.Editor;
import com.myscript.iink.IRenderTarget;
import com.myscript.iink.Renderer;
import com.myscript.iink.graphics.ICanvas;
import com.myscript.iink.graphics.Point;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class EditorView extends FrameLayout implements IRenderTarget, InputController.ViewListener
{
  private int viewWidth;
  private int viewHeight;

  @Nullable
  private Renderer renderer;
  @Nullable
  private Editor editor;
  @Nullable
  private ImageLoader imageLoader;
  @NonNull
  private final OfflineSurfaceManager offlineSurfaceManager;
  @Nullable
  private LayerView layerView;

  private Map<String, Typeface> typefaceMap = new HashMap<>();
  @NonNull
  private List<Canvas.ExtraBrushConfig> extraBrushConfigs = Collections.emptyList();

  public EditorView(Context context)
  {
    this(context, null, 0);
  }

  public EditorView(Context context, @Nullable AttributeSet attrs)
  {
    this(context, attrs, 0);
  }

  public EditorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    offlineSurfaceManager = new OfflineSurfaceManager();
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    // find child render views and initialize them
    for (int i = 0, count = getChildCount(); i < count; ++i)
    {
      View view = getChildAt(i);
      if (view instanceof LayerView)
      {
        layerView = (LayerView) view;

        layerView.setRenderTarget(this);
        if (editor != null)
        {
          layerView.setEditor(editor);
        }
        if (imageLoader != null)
        {
          layerView.setImageLoader(imageLoader);
        }

        layerView.setCustomTypefaces(typefaceMap);
        layerView.setOfflineSurfaceManager(offlineSurfaceManager);
      }
    }
  }

  /**
   * editor creation logic to this view.
   *
   * @param editor the editor to bind to this view to edit content.
   */
  public void setEditor(@Nullable Editor editor)
  {
    this.editor = editor;
    if (editor != null)
    {
      renderer = editor.getRenderer();
      if (layerView != null)
      {
        layerView.setEditor(editor);
      }
      if (viewWidth > 0 && viewHeight > 0)
      {
        editor.setViewSize(viewWidth, viewHeight);
      }
      invalidate(renderer, EnumSet.allOf(IRenderTarget.LayerType.class));
    }
    else
    {
      renderer = null;
    }
  }

  @Nullable
  public Editor getEditor()
  {
    return editor;
  }

  @Nullable
  public Renderer getRenderer()
  {
    return renderer;
  }

  public void setExtraBrushConfigs(@NonNull List<Canvas.ExtraBrushConfig> extraBrushConfigs)
  {
    if (editor != null)
    {
      throw new IllegalStateException("Please set the extra brush configs of the EditorView before binding the editor (through EditorView.setEngine() or EditorView.setEditor())");
    }

    this.extraBrushConfigs = extraBrushConfigs;
    for (int i = 0, count = getChildCount(); i < count; ++i)
    {
      View view = getChildAt(i);
      if (view instanceof LayerView)
      {
        LayerView layerView = (LayerView) view;
        layerView.setExtraBrushConfigs(extraBrushConfigs);
      }
    }
  }

  @NonNull
  public List<Canvas.ExtraBrushConfig> getExtraBrushConfigs()
  {
    return extraBrushConfigs;
  }

  public void setImageLoader(ImageLoader imageLoader)
  {
    this.imageLoader = imageLoader;

    // transfer image loader to render views
    if (layerView != null)
    {
      layerView.setImageLoader(imageLoader);
    }
  }

  @Nullable
  public ImageLoader getImageLoader()
  {
    return imageLoader;
  }

  public void setTypefaces(@NonNull Map<String, Typeface> typefaceMap)
  {
    if (editor != null)
    {
      throw new IllegalStateException("Please set the typeface map of the EditorView before binding the editor (through EditorView.setEngine() or EditorView.setEditor())");
    }

    this.typefaceMap = typefaceMap;
    for (int i = 0, count = getChildCount(); i < count; ++i)
    {
      View view = getChildAt(i);
      if (view instanceof LayerView)
      {
        LayerView layerView = (LayerView) view;
        layerView.setCustomTypefaces(typefaceMap);
      }
    }
  }

  public Map<String, Typeface> getTypefaces()
  {
    return typefaceMap;
  }

  @Override
  protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight)
  {
    viewWidth = newWidth;
    viewHeight = newHeight;

    if (editor != null)
    {
      editor.setViewSize(newWidth, newHeight);
      invalidate(renderer, EnumSet.allOf(IRenderTarget.LayerType.class));
    }

    super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
  }

  @Override
  public final void invalidate(@NonNull Renderer renderer, @NonNull EnumSet<LayerType> layers)
  {
    invalidate(renderer, 0, 0, viewWidth, viewHeight, layers);
  }

  @Override
  public final void invalidate(@NonNull Renderer renderer, int x, int y, int width, int height, @NonNull EnumSet<LayerType> layers)
  {
    if (width <= 0 || height <= 0)
      return;

    if (layerView != null)
    {
      layerView.update(renderer, x, y, width, height);
    }
  }

  @Override
  public void invalidate()
  {
    super.invalidate();
    invalidate(renderer, EnumSet.allOf(LayerType.class));
  }

  @SuppressWarnings("deprecation")
  @Override
  public void invalidate(int l, int t, int r, int b)
  {
    super.invalidate(l, t, r, b);
    invalidate(renderer, l, t, r - l, b - t, EnumSet.allOf(LayerType.class));
  }

  @SuppressWarnings("deprecation")
  @Override
  public void invalidate(Rect dirty)
  {
    super.invalidate(dirty);
    int l = dirty.left;
    int t = dirty.top;
    int w = dirty.width();
    int h = dirty.height();
    invalidate(renderer, l, t, w, h, EnumSet.allOf(LayerType.class));
  }

  @Override
  public boolean supportsOffscreenRendering()
  {
    return true;
  }

  @Override
  public float getPixelDensity()
  {
    return 1f;
  }

  @Override
  public int createOffscreenRenderSurface(int width, int height, boolean alphaOnly)
  {
    return offlineSurfaceManager.create(width, height, alphaOnly);
  }

  @Override
  public void releaseOffscreenRenderSurface(int offscreenID)
  {
    offlineSurfaceManager.release(offscreenID);
  }

  @Override
  public ICanvas createOffscreenRenderCanvas(int offscreenID)
  {
    if (renderer == null)
      throw new IllegalStateException("Cannot create offscreen render canvas if renderer is null");
    if (offscreenID < 0)
      return null;
    Bitmap offlineBitmap = offlineSurfaceManager.getBitmap(offscreenID);
    if (offlineBitmap == null)
      return null;
    android.graphics.Canvas canvas = new android.graphics.Canvas(offlineBitmap);
    return new Canvas(canvas, extraBrushConfigs, typefaceMap, imageLoader, offlineSurfaceManager, renderer.getDpiX(), renderer.getDpiY());
  }

  @Override
  public void showScrollbars()
  {
    int viewHeightPx = editor.getViewHeight();
    int viewWidthPx = editor.getViewWidth();
    Point topLeftPx = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
    Point bottomRightPx = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
    editor.clampViewOffset(topLeftPx);
    editor.clampViewOffset(bottomRightPx);
    float pageHeightPx = bottomRightPx.y - topLeftPx.y + viewHeightPx;
    float pageWidthPx = bottomRightPx.x - topLeftPx.x + viewWidthPx;
    layerView.setScrollbar(renderer, viewWidthPx, (int) pageWidthPx, (int) topLeftPx.x, viewHeightPx, (int) pageHeightPx, (int) topLeftPx.y);
  }
}
