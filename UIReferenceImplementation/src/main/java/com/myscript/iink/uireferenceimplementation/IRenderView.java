package com.myscript.iink.uireferenceimplementation;

import android.graphics.Typeface;

import com.myscript.iink.Editor;
import com.myscript.iink.IRenderTarget;
import com.myscript.iink.Renderer;

import java.util.EnumSet;
import java.util.Map;

/**
 * Implemented by views that render Interactive Ink content.
 */
public interface IRenderView
{
  /**
   * Tells whether this view renders a single layer or all the layers
   * @return <code>true</code> if this view renders a single layer.
   */
  boolean isSingleLayerView();

  /**
   * If the view is a single layer view return the type of the layer it renders.
   * @return the type of the rendered layer.
   */
  IRenderTarget.LayerType getType();

  /**
   * Sets the render target that owns this render view.
   * @param renderTarget the render target.
   */
  void setRenderTarget(IRenderTarget renderTarget);

  /**
   * Sets the editor that holds the content to render.
   * @param editor the editor.
   */
  void setEditor(Editor editor);

  /**
   * Sets the image loader used to render images.
   * @param imageLoader the image loader.
   */
  void setImageLoader(ImageLoader imageLoader);

  /**
   * Sets the map of custom typefaces to use for text rendering.
   * @param typefaceMap the map of custom typefaces.
   */
  void setCustomTypefaces(Map<String, Typeface> typefaceMap);

  /**
   * Requests an update of the specified area of the view.
   * @param renderer the renderer to be used to render the area.
   * @param x the area top x position.
   * @param y the area top y position.
   * @param width the area width.
   * @param height the area height.
   * @param layers the layers to update. To be ignored if this is a single layer view.
   */
  void update(Renderer renderer, int x, int y, int width, int height, EnumSet<IRenderTarget.LayerType> layers);
}
