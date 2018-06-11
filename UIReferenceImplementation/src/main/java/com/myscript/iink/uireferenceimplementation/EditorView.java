// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.myscript.iink.Configuration;
import com.myscript.iink.Editor;
import com.myscript.iink.Engine;
import com.myscript.iink.IRenderTarget;
import com.myscript.iink.Renderer;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class EditorView extends FrameLayout implements IRenderTarget
{
  private static final String TAG = "EditorView";

  private int viewWidth;
  private int viewHeight;

  @Nullable
  private Engine engine;

  @Nullable
  private Renderer renderer;
  @Nullable
  private Editor editor;

  @Nullable
  private InputController inputController;
  private ImageLoader imageLoader;

  private LayerView[] layerViews = new LayerView[LayerType.values().length];

  private Map<String, Typeface> typefaceMap = new HashMap<>();

  @Nullable
  private SmartGuideView smartGuideView;

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

    smartGuideView = null;
  }

  public void close()
  {
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

  @Nullable
  Engine getEngine()
  {
    return engine;
  }

  public void setEngine(@NonNull Engine engine)
  {
    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

    this.engine = engine;
    Configuration conf = engine.getConfiguration();
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

    loadFonts();

    for (int i = 0, count = getChildCount(); i < count; ++i)
    {
      View view = getChildAt(i);
      if (view instanceof LayerView)
      {
        ((LayerView) view).setCustomTypefaces(typefaceMap);
      }
    }
    renderer = engine.createRenderer(displayMetrics.xdpi, displayMetrics.ydpi, this);

    editor = engine.createEditor(renderer);
    editor.setFontMetricsProvider(new FontMetricsProvider(displayMetrics, typefaceMap));

    smartGuideView = findViewById(R.id.smart_guide_view);
    smartGuideView.setEditor(editor);
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

  public void setInputController(InputController inputController)
  {
    this.inputController = inputController;
    smartGuideView.setSmartGuideMoreHandler(inputController.getListener());
  }

  public void setImageLoader(ImageLoader imageLoader)
  {
    this.imageLoader = imageLoader;

    for (int i = 0, count = getChildCount(); i < count; ++i)
    {
      View view = getChildAt(i);
      if (view instanceof LayerView)
      {
        ((LayerView) view).setImageLoader(imageLoader);
      }
    }
  }

  public ImageLoader getImageLoader()
  {
    return imageLoader;
  }

  public void setInputMode(int inputMode)
  {
    inputController.setInputMode(inputMode);
  }

  public int getInputMode()
  {
    return inputController.getInputMode();
  }

  public void setSmartGuideMoreHandler(IInputControllerListener smartGuideMoreHandler)
  {
    smartGuideView.setSmartGuideMoreHandler(smartGuideMoreHandler);
  }
  
  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    for (int i = 0, count = getChildCount(); i < count; ++i)
    {
      View view = getChildAt(i);
      if (view instanceof LayerView)
      {
        LayerView layerView = (LayerView) view;
        layerViews[layerView.getType().ordinal()] = layerView;
        layerView.setRenderTarget(this);
      }
    }
  }

  @Override
  protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight)
  {
    viewWidth = newWidth;
    viewHeight = newHeight;

    editor.setViewSize(newWidth, newHeight);
    invalidate(renderer, EnumSet.allOf(IRenderTarget.LayerType.class));

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

    for (LayerType type : layers)
    {
      LayerView layerView = layerViews[type.ordinal()];
      if (layerView != null)
        layerView.update(renderer, x, y, width, height);
    }
  }

  private void loadFonts()
  {
    AssetManager assets = this.getContext().getApplicationContext().getAssets();
    try
    {
      String assetsDir = "fonts";
      String[] files = assets.list(assetsDir);
      for (String filename : files)
      {
        String fontPath = assetsDir + File.separatorChar + filename;
        String fontFamily = FontUtils.getFontFamily(assets, fontPath);
        final Typeface typeface = Typeface.createFromAsset(assets, fontPath);
        if (fontFamily != null && typeface != null)
        {
          typefaceMap.put(fontFamily, typeface);
        }
      }
    }
    catch (IOException e)
    {
      Log.e(TAG, "Failed to list fonts from assets", e);
    }
  }
}
