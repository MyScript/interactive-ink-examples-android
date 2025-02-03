// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.graphics.Bitmap;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Xfermode;
import android.text.TextPaint;
import android.util.Log;

import com.myscript.iink.GLRenderer;
import com.myscript.iink.ParameterSet;
import com.myscript.iink.graphics.Color;
import com.myscript.iink.graphics.ExtraBrushStyle;
import com.myscript.iink.graphics.FillRule;
import com.myscript.iink.graphics.ICanvas;
import com.myscript.iink.graphics.IPath;
import com.myscript.iink.graphics.InkPoints;
import com.myscript.iink.graphics.LineCap;
import com.myscript.iink.graphics.LineJoin;
import com.myscript.iink.graphics.Style;
import com.myscript.iink.graphics.Transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

public class Canvas implements ICanvas
{

  private static final Style DEFAULT_SVG_STYLE = new Style();
  private static final PorterDuffXfermode xferModeSrcOver = new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER);

  @Nullable
  private android.graphics.Canvas canvas;

  @NonNull
  private final Paint strokePaint;

  @NonNull
  private final Paint bitmapAlphaPaint;
  @NonNull
  private final Paint clearPaint;

  @NonNull
  private final TextPaint textPaint;

  @NonNull
  private final Paint fillPaint;
  @Nullable
  private FillRule fillRule;

  @NonNull
  private Transform transform;
  @NonNull
  private final Matrix transformMatrix;
  @NonNull
  private final float[] transformValues;

  // Cache variable to prevent garbage collection
  @NonNull
  private final float[] pointsCache = new float[4];
  @NonNull
  private final Rect simpleRectCache = new Rect();
  @NonNull
  private final RectF floatRectCache = new RectF();

  @Nullable
  private final ImageLoader imageLoader;
  private final OfflineSurfaceManager offlineSurfaceManager;
  @Nullable
  private GLRenderer glRenderer;
  private boolean keepGLRenderer = false;

  private boolean clearOnStartDraw = true;

  private final List<String> clips;

  private final Map<String, Typeface> typefaceMap;

  private float[] dashArray;
  private float dashOffset = 0;

  private final float xdpi;
  private final float ydpi;

  @NonNull
  private final Matrix textScaleMatrix;
  @NonNull
  private final Matrix pointScaleMatrix;

  public static class ExtraBrushConfig
  {
    @NonNull
    public final String baseName;
    @NonNull
    public final Bitmap stampBitmap;
    @Nullable
    public final Bitmap backgroundBitmap;
    @NonNull
    public final ParameterSet config;

    public ExtraBrushConfig(@NonNull String baseName, @NonNull Bitmap stampBitmap, @Nullable Bitmap backgroundBitmap, @NonNull ParameterSet config)
    {
      this.baseName = baseName;
      this.stampBitmap = stampBitmap;
      this.backgroundBitmap = backgroundBitmap;
      this.config = config;
    }
  }

  public Canvas(@Nullable android.graphics.Canvas canvas, Map<String, Typeface> typefaceMap, ImageLoader imageLoader, float xdpi, float ydpi)
  {
    this(canvas, Collections.emptyList(), typefaceMap, imageLoader, null, xdpi, ydpi);
  }

  public Canvas(@Nullable android.graphics.Canvas canvas, @NonNull List<ExtraBrushConfig> extraBrushConfigs, Map<String, Typeface> typefaceMap, ImageLoader imageLoader, float xdpi, float ydpi)
  {
    this(canvas, extraBrushConfigs, typefaceMap, imageLoader, null, xdpi, ydpi);
  }

  public Canvas(@Nullable android.graphics.Canvas canvas, @NonNull List<ExtraBrushConfig> extraBrushConfigs, Map<String, Typeface> typefaceMap, ImageLoader imageLoader, @Nullable OfflineSurfaceManager offlineSurfaceManager, float xdpi, float ydpi)
  {
    this.canvas = canvas;
    this.typefaceMap = typefaceMap;
    this.imageLoader = imageLoader;
    this.offlineSurfaceManager = offlineSurfaceManager;
    this.xdpi = xdpi;
    this.ydpi = ydpi;

    if (!extraBrushConfigs.isEmpty() && GLRenderer.isDeviceSupported())
    {
      glRenderer = new GLRenderer();
      for (ExtraBrushConfig config : extraBrushConfigs)
        glRenderer.configureBrush(config.baseName, config.stampBitmap, config.backgroundBitmap, config.config);
    }

    clips = new ArrayList<>();

    strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    strokePaint.setStyle(Paint.Style.STROKE);

    textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    bitmapAlphaPaint = new Paint();
    clearPaint = new Paint();
    clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    clearPaint.setStyle(Paint.Style.FILL);
    clearPaint.setColor(android.graphics.Color.TRANSPARENT);

    fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    fillPaint.setStyle(Paint.Style.FILL);

    transform = new Transform();
    transformMatrix = new Matrix();
    transformValues = new float[9];
    transformValues[Matrix.MPERSP_0] = 0;
    transformValues[Matrix.MPERSP_1] = 0;
    transformValues[Matrix.MPERSP_2] = 1;

    dashArray = null;

    textScaleMatrix = new Matrix();
    textScaleMatrix.setScale(25.4f / xdpi, 25.4f / ydpi);
    pointScaleMatrix = new Matrix();
    textScaleMatrix.invert(pointScaleMatrix);

    // it is mandatory to configure the Paint with SVG defaults represented by default Style object
    applyStyle(DEFAULT_SVG_STYLE);
  }

  public void destroy()
  {
    if (glRenderer != null)
    {
      glRenderer.destroy();
      glRenderer = null;
    }
  }

  public void setCanvas(@NonNull android.graphics.Canvas canvas)
  {
    this.canvas = canvas;
  }

  public void setClearOnStartDraw(boolean clearOnStartDraw)
  {
    this.clearOnStartDraw = clearOnStartDraw;
  }

  public void setKeepGLRenderer(boolean keepGLRenderer)
  {
    this.keepGLRenderer = keepGLRenderer;
  }

  private void applyStyle(@NonNull Style style)
  {
    setStrokeColor(style.getStrokeColor());
    setStrokeWidth(style.getStrokeWidth());
    setStrokeLineCap(style.getStrokeLineCap());
    setStrokeLineJoin(style.getStrokeLineJoin());
    setStrokeMiterLimit(style.getStrokeMiterLimit());
    setStrokeDashArray(style.getStrokeDashArray());
    setStrokeDashOffset(style.getStrokeDashOffset());
    setFillColor(style.getFillColor());
    setFillRule(style.getFillRule());

    setDropShadow(style.getDropShadowXOffset(), style.getDropShadowYOffset(), style.getDropShadowRadius(), style.getDropShadowColor());

    setFontProperties(style.getFontFamily(), style.getFontLineHeight(), style.getFontSize(),
                      style.getFontStyle(), style.getFontVariant(), style.getFontWeight());
  }

  @ColorInt
  private static int argb(@NonNull Color color)
  {
    return android.graphics.Color.argb(color.a(), color.r(), color.g(), color.b());
  }

  @Override
  public void setTransform(@NonNull Transform transform)
  {
    transformValues[Matrix.MSCALE_X] = (float) transform.xx;
    transformValues[Matrix.MSKEW_X] = (float) transform.yx;
    transformValues[Matrix.MTRANS_X] = (float) transform.tx;
    transformValues[Matrix.MSKEW_Y] = (float) transform.xy;
    transformValues[Matrix.MSCALE_Y] = (float) transform.yy;
    transformValues[Matrix.MTRANS_Y] = (float) transform.ty;

    Objects.requireNonNull(canvas);
    transformMatrix.setValues(transformValues);
    canvas.setMatrix(transformMatrix);

    this.transform = transform;
  }

  @NonNull
  @Override
  public Transform getTransform()
  {
    return transform;
  }

  @Override
  public void setStrokeColor(@NonNull Color strokeColor)
  {
    strokePaint.setColor(argb(strokeColor));
  }

  @Override
  public void setStrokeWidth(float strokeWidth)
  {
    strokePaint.setStrokeWidth(strokeWidth);
  }

  @Override
  public void setStrokeLineCap(@NonNull LineCap strokeLineCap)
  {
    switch (strokeLineCap)
    {
      case BUTT:
        strokePaint.setStrokeCap(Paint.Cap.BUTT);
        break;
      case ROUND:
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        break;
      case SQUARE:
        strokePaint.setStrokeCap(Paint.Cap.SQUARE);
        break;
      default:
        throw new IllegalArgumentException("Unsupported LineCap");
    }
  }

  @Override
  public void setStrokeLineJoin(@NonNull LineJoin strokeLineJoin)
  {
    switch (strokeLineJoin)
    {
      case MITER:
        strokePaint.setStrokeJoin(Paint.Join.MITER);
        break;
      case ROUND:
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        break;
      case BEVEL:
        strokePaint.setStrokeJoin(Paint.Join.BEVEL);
        break;
      default:
        throw new IllegalArgumentException("Unsupported LineJoin");
    }
  }

  @Override
  public void setStrokeMiterLimit(float strokeMiterLimit)
  {
    strokePaint.setStrokeMiter(strokeMiterLimit);
  }

  @Override
  public void setStrokeDashArray(float[] strokeDashArray)
  {
    if (strokeDashArray == null || strokeDashArray.length == 0)
    {
      dashArray = null;
    }
    else
    {
      dashArray = new float[strokeDashArray.length];
      System.arraycopy(strokeDashArray, 0, dashArray, 0, strokeDashArray.length);
    }

    if (dashArray != null)
      strokePaint.setPathEffect(new DashPathEffect(dashArray, dashOffset));
    else
      strokePaint.setPathEffect(null);
  }

  @Override
  public void setStrokeDashOffset(float strokeDashOffset)
  {
    dashOffset = strokeDashOffset;
    if (dashArray != null)
      strokePaint.setPathEffect(new DashPathEffect(dashArray, dashOffset));
    else
      strokePaint.setPathEffect(null);
  }

  @Override
  public void setFillColor(@NonNull Color fillColor)
  {
    @ColorInt int color = argb(fillColor);
    textPaint.setColor(color);
    fillPaint.setColor(color);
  }

  @Override
  public void setFillRule(@NonNull FillRule fillRule)
  {
    this.fillRule = fillRule;
  }

  @Override
  public void setDropShadow(float xOffset, float yOffset, float radius, @NonNull Color color)
  {
    @ColorInt int androidColor = argb(color);
    boolean isTransparent = color.a() == 0;
    int opaqueColor = ColorUtils.setAlphaComponent(androidColor, 0xFF);
    strokePaint.setShadowLayer(radius / 20f, xOffset, yOffset, isTransparent ? android.graphics.Color.TRANSPARENT : opaqueColor);
    textPaint.setShadowLayer(radius / 10f, xOffset * 2.5f, yOffset * 5f, isTransparent ? android.graphics.Color.TRANSPARENT : opaqueColor);
    fillPaint.setShadowLayer(radius / 20f, xOffset, yOffset, isTransparent ? android.graphics.Color.TRANSPARENT : opaqueColor);
  }

  @Override
  public final void setFontProperties(@NonNull String fontFamily, float fontLineHeight, float fontSize, @NonNull String fontStyle,
                                      @NonNull String fontVariant, int fontWeight)
  {
    Typeface typeface = typefaceMap == null
        ? FontUtils.getTypeface(fontFamily, fontStyle, fontVariant, fontWeight)
        : FontUtils.getTypeface(typefaceMap, fontFamily, fontStyle, fontVariant, fontWeight);

    // scale font size to the canvas transform scale, to ensure best font rendering
    // (text size is expressed in pixels, while fontSize is in mm)
    textPaint.setTypeface(typeface);
    textPaint.setTextSize(Math.round((fontSize / 25.4f) * ydpi));
  }

  @Override
  public void startDraw(int x, int y, int width, int height)
  {
    Objects.requireNonNull(canvas);
    canvas.save();

    pointsCache[0] = x;
    pointsCache[1] = y;
    pointsCache[2] = x + width;
    pointsCache[3] = y + height;

    // When offscreen rendering is supported, clear the destination
    // Otherwise, do not clear the destination (e.g. when exporting image, we want a white background)
    if (offlineSurfaceManager != null && clearOnStartDraw)
      canvas.drawRect(pointsCache[0], pointsCache[1], pointsCache[2], pointsCache[3], clearPaint);

    // Hardware canvas does not support PorterDuffXfermode
    canvas.clipRect(pointsCache[0], pointsCache[1], pointsCache[2], pointsCache[3]);
  }

  @Override
  public void endDraw()
  {
    if (!keepGLRenderer && glRenderer != null)
    {
      glRenderer.destroy();
      glRenderer = null;
    }

    Objects.requireNonNull(canvas);
    canvas.restore();
  }

  @Override
  public void startGroup(@NonNull String id, float x, float y, float width, float height, boolean clipContent)
  {
    if (clipContent)
    {
      Objects.requireNonNull(canvas);
      clips.add(id);
      canvas.save();

      canvas.clipRect(x, y, x + width, y + height);
    }
  }

  @Override
  public void endGroup(@NonNull String id)
  {
    int index = clips.lastIndexOf(id);
    if (index != -1)
    {
      Objects.requireNonNull(canvas);
      canvas.restore();
      clips.remove(index);
    }
  }

  @Override
  public void startItem(@NonNull String id)
  {
    // no-op
  }

  @Override
  public void endItem(@NonNull String id)
  {
    // no-op
  }

  @NonNull
  @Override
  public final IPath createPath()
  {
    return new Path();
  }

  @Override
  public void drawPath(@NonNull IPath ipath)
  {
    Objects.requireNonNull(canvas);
    Path path = (Path) ipath;

    if (android.graphics.Color.alpha(fillPaint.getColor()) != 0)
    {
      path.setFillType(fillRule == FillRule.EVENODD ? android.graphics.Path.FillType.EVEN_ODD : android.graphics.Path.FillType.WINDING);
      canvas.drawPath(path, fillPaint);
    }
    if (android.graphics.Color.alpha(strokePaint.getColor()) != 0)
    {
      canvas.drawPath(path, strokePaint);
    }
  }

  @Override
  public boolean isExtraBrushSupported(@NonNull String brushName)
  {
    return glRenderer != null && glRenderer.isBrushSupported(brushName);
  }

  @Override
  public void drawStrokeWithExtraBrush(@NonNull InkPoints[] vInkPoints, int temporaryPoints,
                                       @NonNull ExtraBrushStyle style, boolean fullStroke, long id)
  {
    Objects.requireNonNull(canvas);

    if (!isExtraBrushSupported(style.brushName))
      return;

    if (vInkPoints.length == 0 || vInkPoints[0].x.length == 0 || style.strokeWidth <= 0.f || android.graphics.Color.alpha(fillPaint.getColor()) == 0)
      return;

    if (!glRenderer.isInitialized())
    {
      glRenderer.initialize(keepGLRenderer, canvas.getWidth(), canvas.getHeight(), xdpi, ydpi);
    }

    Xfermode xfm = fillPaint.getXfermode();

    try
    {
      canvas.setMatrix(null); // GLRenderer works with pixels
      fillPaint.setXfermode(xferModeSrcOver);

      PointF strokeOrigin = glRenderer.drawStroke(vInkPoints, temporaryPoints, transformValues, style, fillPaint, fullStroke, id);
      Bitmap strokeBitmap = glRenderer.saveStroke();
      if (strokeBitmap != null)
        canvas.drawBitmap(strokeBitmap, strokeOrigin.x, strokeOrigin.y, fillPaint);

      if (temporaryPoints > 0 && vInkPoints.length == 1)
      {
        PointF temporaryOrigin = glRenderer.drawTemporary(vInkPoints, temporaryPoints, transformValues, style, fillPaint);
        Bitmap temporaryBitmap = glRenderer.saveTemporary();
        if (temporaryBitmap != null)
          canvas.drawBitmap(temporaryBitmap, temporaryOrigin.x, temporaryOrigin.y, fillPaint);
      }
    }
    catch (Exception e)
    {
      Log.e("Canvas", "Error trying to draw stroke with extra brush: " + e.getMessage(), e);
    }
    finally
    {
      // restore
      fillPaint.setXfermode(xfm);
      canvas.setMatrix(transformMatrix);
    }
  }

  @Override
  public void drawRectangle(float x, float y, float width, float height)
  {
    Objects.requireNonNull(canvas);
    if (android.graphics.Color.alpha(fillPaint.getColor()) != 0)
    {
      canvas.drawRect(x, y, x + width, y + height, fillPaint);
    }
    if (android.graphics.Color.alpha(strokePaint.getColor()) != 0)
    {
      canvas.drawRect(x, y, x + width, y + height, strokePaint);
    }
  }

  @Override
  public void drawLine(float x1, float y1, float x2, float y2)
  {
    Objects.requireNonNull(canvas);
    canvas.drawLine(x1, y1, x2, y2, strokePaint);
  }

  @Override
  public void drawObject(@NonNull String url, @NonNull String mimeType, float x, float y, float width, float height)
  {
    if (imageLoader == null)
      return;

    Objects.requireNonNull(canvas);

    RectF pixelSize = new RectF(x,y,x + width, y + height);
    transformMatrix.mapRect(pixelSize);

    final Rect targetRect = new Rect(
        (int) Math.floor(pixelSize.left),
        (int) Math.floor(pixelSize.top),
        (int) (Math.ceil(pixelSize.right)),
        (int) (Math.ceil(pixelSize.bottom)));

    synchronized (imageLoader)
    {
      Bitmap image = imageLoader.getImage(url, mimeType, targetRect.width(), targetRect.height());

      if (image == null)
      {
        // image is not ready yet...
        if (android.graphics.Color.alpha(fillPaint.getColor()) != 0)
        {
          canvas.drawRect(x, y, width, height, fillPaint);
        }
      }
      else
      {
        // draw the image
        Rect srcRect = new Rect(0, 0, image.getWidth(), image.getHeight());
        RectF dstRect = new RectF(x, y, x + width, y + height);
        if (!image.isRecycled())
          canvas.drawBitmap(image, srcRect, dstRect, null);
        else
          Log.e("Canvas", "Trying to draw recycled Bitmap");
      }
    }
  }

  @Override
  public void drawText(@NonNull String label, float x, float y, float xmin, float ymin, float xmax, float ymax)
  {
    Objects.requireNonNull(canvas);
    // transform the insertion point so that it is not impacted by text scale
    pointsCache[0] = x;
    pointsCache[1] = y;
    pointScaleMatrix.mapPoints(pointsCache);

    // transform the text to account for font size in pixel (not mm)
    canvas.concat(textScaleMatrix);

    // draw text
    canvas.drawText(label, pointsCache[0], pointsCache[1], textPaint);

    // restore transform
    canvas.setMatrix(transformMatrix);
  }

  @Override
  public void blendOffscreen(int id, float srcX, float srcY, float srcWidth, float srcHeight,
                             float destX, float destY, float destWidth, float destHeight, @NonNull Color blendColor)
  {
    if (offlineSurfaceManager != null)
    {
      Bitmap bitmap = offlineSurfaceManager.getBitmap(id);

      if (bitmap != null)
      {
        Objects.requireNonNull(canvas);
        floatRectCache.set(destX, destY, destX + destWidth, destY + destHeight);
        simpleRectCache.set(Math.round(srcX), Math.round(srcY),
            Math.round(srcX + srcWidth), Math.round(srcY + srcHeight));
        bitmapAlphaPaint.setColor(argb(blendColor));

        canvas.drawBitmap(bitmap,
            simpleRectCache, floatRectCache,
            bitmapAlphaPaint);
      }
    }
  }
}
