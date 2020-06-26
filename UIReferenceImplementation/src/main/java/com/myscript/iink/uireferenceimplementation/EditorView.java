// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;

import com.myscript.iink.Configuration;
import com.myscript.iink.Editor;
import com.myscript.iink.Engine;
import com.myscript.iink.IRenderTarget2;
import com.myscript.iink.Renderer;
import com.myscript.iink.graphics.ICanvas2;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class EditorView extends FrameLayout implements IRenderTarget2
{
  private int viewWidth;
  private int viewHeight;
  private float pixelDensity;

  @Nullable
  private Renderer renderer;
  @Nullable
  private Editor editor;

  @Nullable
  private InputController inputController;
  @Nullable
  private ImageLoader imageLoader;
  @Nullable
  private OfflineSurfaceManager offlineSurfaceManager;

  @Nullable
  private IRenderView renderView;
  @Nullable
  private IRenderView[] layerViews;

  private Map<String, Typeface> typefaceMap = new HashMap<>();

  @Nullable
  private SmartGuideView smartGuideView;

  public EditorView(Context context)
  {
    super(context);
  }

  public EditorView(Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
  }

  public EditorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();

    offlineSurfaceManager = new OfflineSurfaceManager();

    // find child render views and initialize them
    for (int i = 0, count = getChildCount(); i < count; ++i)
    {
      View view = getChildAt(i);
      if (view instanceof IRenderView)
      {
        IRenderView renderView = (IRenderView) view;
        if (renderView.isSingleLayerView())
        {
          if (layerViews == null)
            layerViews = new IRenderView[2];
          if (renderView.getType() == LayerType.BACKGROUND ||renderView.getType() == LayerType.MODEL ||renderView.getType() == LayerType.TEMPORARY)
            layerViews[0] = renderView;
          else if (renderView.getType() == LayerType.CAPTURE)
            layerViews[1] = renderView;
          else
            throw new RuntimeException("Unknown layer view type");
        }
        else
        {
          this.renderView = renderView;
        }

        renderView.setRenderTarget(this);
        if (editor != null) // if null it will be transferred in setEngine() below
          renderView.setEditor(editor);
        if (imageLoader != null) // if null it will be transferred in setImageLoader() below
          renderView.setImageLoader(imageLoader);

        renderView.setCustomTypefaces(typefaceMap);

        if (view instanceof LayerView)
        {
          LayerView layerView = (LayerView) view;
          layerView.setOfflineSurfaceManager(offlineSurfaceManager);
        }
      }
    }

    smartGuideView = findViewById(R.id.smart_guide_view);
  }

  public void close()
  {
    // unplug input management
    setOnTouchListener(null);

    if (editor != null && !editor.isClosed())
    {
      editor.setPart(null);
      editor.setFontMetricsProvider(null);
      editor.close();
      editor = null;
    }

    if (renderer != null && !renderer.isClosed())
    {
      renderer.close();
      renderer = null;
    }
  }

  public void setEngine(@NonNull Engine engine)
  {
    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

    pixelDensity = 1;

    renderer = engine.createRenderer(displayMetrics.xdpi, displayMetrics.ydpi, this);

    editor = engine.createEditor(renderer);
    editor.setFontMetricsProvider(new FontMetricsProvider(displayMetrics, typefaceMap));

    Configuration conf = editor.getConfiguration();
    float verticalMarginPX = getResources().getDimension(R.dimen.vertical_margin);
    float horizontalMarginPX = getResources().getDimension(R.dimen.horizontal_margin);
    float verticalMarginMM = 25.4f * verticalMarginPX / displayMetrics.ydpi;
    float horizontalMarginMM = 25.4f * horizontalMarginPX / displayMetrics.xdpi;
    conf.setNumber("text.margin.top", verticalMarginMM);
    conf.setNumber("text.margin.left", horizontalMarginMM);
    conf.setNumber("text.margin.right", horizontalMarginMM);
    conf.setNumber("math.margin.top", verticalMarginMM);
    conf.setNumber("math.margin.bottom", verticalMarginMM);
    conf.setNumber("math.margin.left", horizontalMarginMM);
    conf.setNumber("math.margin.right", horizontalMarginMM);

    smartGuideView = findViewById(R.id.smart_guide_view);
    smartGuideView.setEditor(editor);

    inputController = new InputController(getContext(), this, getEditor());
    setOnTouchListener(inputController);

    // transfer editor to render views
    if (renderView != null)
    {
      renderView.setEditor(editor);
    }
    else if (layerViews != null)
    {
      for (int i = 0; i < layerViews.length; ++i)
      {
        if (layerViews[i] != null)
          layerViews[i].setEditor(editor);
      }
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

  public void setInputControllerListener(IInputControllerListener listener)
  {
    if (inputController != null)
    {
      inputController.setListener(listener);
      smartGuideView.setSmartGuideMoreHandler(listener);
    }
  }

  public void setImageLoader(ImageLoader imageLoader)
  {
    this.imageLoader = imageLoader;

    // transfer image loader to render views
    if (renderView != null)
    {
      renderView.setImageLoader(imageLoader);
    }
    else if (layerViews != null)
    {
      for (int i = 0; i < layerViews.length; ++i)
      {
        if (layerViews[i] != null)
          layerViews[i].setImageLoader(imageLoader);
      }
    }
  }

  public void setTypefaces(@NonNull Map<String, Typeface> typefaceMap)
  {
    if (editor != null)
      throw new IllegalStateException("Please set the typeface map of the EditorView before calling EditorView.setEngine()");

    this.typefaceMap = typefaceMap;
    for (int i = 0, count = getChildCount(); i < count; ++i)
    {
      View view = getChildAt(i);
      if (view instanceof IRenderView)
      {
        IRenderView renderView = (IRenderView) view;
        renderView.setCustomTypefaces(typefaceMap);
      }
    }
  }

  public Map<String, Typeface> getTypefaces()
  {
    return typefaceMap;
  }

  public ImageLoader getImageLoader()
  {
    return imageLoader;
  }

  public void setInputMode(int inputMode)
  {
    if (inputController != null)
    {
      inputController.setInputMode(inputMode);
    }
  }

  public int getInputMode()
  {
    return inputController != null ? inputController.getInputMode() : InputController.INPUT_MODE_NONE;
  }

  @Override
  protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight)
  {
    viewWidth = newWidth;
    viewHeight = newHeight;

    if (editor != null)
    {
      editor.setViewSize(newWidth, newHeight);
      invalidate(renderer, EnumSet.allOf(IRenderTarget2.LayerType.class));
    }

    super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
  }

  @Override
  public final void invalidate(Renderer renderer, EnumSet<LayerType> layers)
  {
    invalidate(renderer, 0, 0, viewWidth, viewHeight, layers);
  }

  @Override
  public final void invalidate(Renderer renderer, int x, int y, int width, int height, EnumSet<LayerType> layers)
  {
    if (width <= 0 || height <= 0)
      return;

    if (renderView != null)
    {
      renderView.update(renderer, x, y, width, height, layers);
    }
    else if (layerViews != null)
    {
      for (LayerType type : layers)
      {
        int layerID = 1;
        if (type == LayerType.BACKGROUND ||type == LayerType.MODEL ||type == LayerType.TEMPORARY)
          layerID = 0;
        IRenderView layerView = layerViews[layerID];
        if (layerView != null)
          layerView.update(renderer, x, y, width, height, layers);
      }
    }
  }

  @Override
  public void invalidate()
  {
    super.invalidate();
    invalidate(renderer, EnumSet.allOf(LayerType.class));
  }

  @Override
  public void invalidate(int l, int t, int r, int b)
  {
    super.invalidate(l, t, r, b);
    invalidate(renderer, l, t, r - l, b - t, EnumSet.allOf(LayerType.class));
  }

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
    return offlineSurfaceManager != null;
  }

  @Override
  public float getPixelDensity()
  {
    return pixelDensity;
  }

  @Override
  public int createOffscreenRenderSurface(int width, int height, boolean alphaOnly)
  {
    if (offlineSurfaceManager == null)
      return -1;
    return offlineSurfaceManager.create(width, height, alphaOnly);
  }

  @Override
  public void releaseOffscreenRenderSurface(int offscreenID)
  {
    if (offlineSurfaceManager != null)
      offlineSurfaceManager.release(offscreenID);
  }

  @Override
  public ICanvas2 createOffscreenRenderCanvas(int offscreenID)
  {
    if (offscreenID < 0 || offlineSurfaceManager == null)
      return null;
    Bitmap offlineBitmap = offlineSurfaceManager.getBitmap(offscreenID);
    if (offlineBitmap == null)
      return null;
    android.graphics.Canvas canvas = new android.graphics.Canvas(offlineBitmap);
    return new Canvas(canvas, typefaceMap, imageLoader, this, offlineSurfaceManager);
  }
}
