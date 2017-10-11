// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextPaint;
import android.util.DisplayMetrics;

import com.myscript.iink.IRenderTarget;
import com.myscript.iink.graphics.Color;
import com.myscript.iink.graphics.FillRule;
import com.myscript.iink.graphics.ICanvas;
import com.myscript.iink.graphics.IPath;
import com.myscript.iink.graphics.LineCap;
import com.myscript.iink.graphics.LineJoin;
import com.myscript.iink.graphics.Point;
import com.myscript.iink.graphics.Style;
import com.myscript.iink.graphics.Transform;
import com.myscript.util.Numbers;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Canvas implements ICanvas
{

  private static final Style DEFAULT_SVG_STYLE = new Style();

  @NonNull
  private android.graphics.Canvas canvas;

  @NonNull
  private Paint strokePaint;

  @NonNull
  private TextPaint textPaint;

  @NonNull
  private Paint fillPaint;
  @Nullable
  private FillRule fillRule;

  @NonNull Transform transform;
  @NonNull
  private Matrix transformMatrix;
  @NonNull
  private float[] transformValues;
  @NonNull Matrix identityMatrix;


  @Nullable
  private ImageLoader imageLoader;
  private IRenderTarget target;

  private Set<String> clips;

  private Map<String, Typeface> typefaceMap;

  private float[] dashArray;
  private int dashOffset = 0;

  DisplayMetrics displayMetrics;

  public Canvas(@NonNull android.graphics.Canvas canvas, Map<String, Typeface> typefaceMap, ImageLoader imageLoader, IRenderTarget target)
  {
    this.canvas = canvas;
    this.typefaceMap = typefaceMap;
    this.imageLoader = imageLoader;
    this.target = target;

    clips = new HashSet<>();

    strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    strokePaint.setStyle(Paint.Style.STROKE);

    textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    fillPaint.setStyle(Paint.Style.FILL);

    transform = new Transform();
    transformMatrix = new Matrix();
    transformValues = new float[9];
    transformValues[Matrix.MPERSP_0] = 0;
    transformValues[Matrix.MPERSP_1] = 0;
    transformValues[Matrix.MPERSP_2] = 1;
    identityMatrix = new Matrix();

    dashArray = null;

    displayMetrics = Resources.getSystem().getDisplayMetrics();

    // it is mandatory to configure the Paint with SVG defaults represented by default Style object
    applyStyle(DEFAULT_SVG_STYLE);
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

    transformMatrix.setValues(transformValues);
    canvas.setMatrix(transformMatrix);

    // transform has changed: update font size accordingly
    float textSize = textPaint.getTextSize() * (float)(transform.yy / this.transform.yy);
    textPaint.setTextSize(textSize);

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
        assert false : "Unsupported LineCap";
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
        assert false : "Unsupported LineJoin";
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
  public final void setFontProperties(@NonNull String fontFamily, float fontLineHeight, float fontSize, String fontStyle,
                                      @NonNull String fontVariant, int fontWeight)
  {
    Typeface typeface = typefaceMap == null ?
        FontUtils.getTypeface(fontFamily, fontStyle, fontVariant, fontWeight) :
        FontUtils.getTypeface(typefaceMap, fontFamily, fontStyle, fontVariant, fontWeight);

    // scale font size to the canvas transform scale, to ensure best font rendering
    // (text size is expressed in pixels, while fontSize is in mm)
    textPaint.setTypeface(typeface);
    textPaint.setTextSize(fontSize * (float)transform.yy);
  }

  @Override
  public void startGroup(@NonNull String id, float x, float y, float width, float height, boolean clipContent)
  {
    if (clipContent)
    {
      clips.add(id);
      canvas.save();

      canvas.clipRect(x, y, x + width, y + height);
    }
  }

  @Override
  public void endGroup(@NonNull String id)
  {
    if (clips.remove(id))
    {
      canvas.restore();
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

  @Override
  public final IPath createPath()
  {
    return new Path();
  }

  @Override
  public void drawPath(@NonNull IPath ipath)
  {
    Path path = (Path) ipath;

    if (fillPaint.getColor() != android.graphics.Color.TRANSPARENT)
    {
      path.setFillType(fillRule == FillRule.EVENODD ? android.graphics.Path.FillType.EVEN_ODD : android.graphics.Path.FillType.WINDING);
      canvas.drawPath(path, fillPaint);
    }
    if (strokePaint.getColor() != android.graphics.Color.TRANSPARENT)
    {
      canvas.drawPath(path, strokePaint);
    }
  }

  @Override
  public void drawRectangle(float x, float y, float width, float height)
  {
    if (fillPaint.getColor() != android.graphics.Color.TRANSPARENT)
    {
      canvas.drawRect(x, y, x + width, y + height, fillPaint);
    }
    if (strokePaint.getColor() != android.graphics.Color.TRANSPARENT)
    {
      canvas.drawRect(x, y, x + width, y + height, strokePaint);
    }
  }

  @Override
  public void drawLine(float x1, float y1, float x2, float y2)
  {
    canvas.drawLine(x1, y1, x2, y2, strokePaint);
  }

  @Override
  public void drawObject(@NonNull String url, @NonNull String mimeType, float x, float y, float width, float height)
  {
    if (imageLoader == null)
      return;

    Point screenMin = new Point(x, y);
    transform.apply(screenMin);
    Point screenMax = new Point(x + width, y + height);
    transform.apply(screenMax);

    final Rect targetRect = new Rect(
        (int)Math.floor(screenMin.x),
        (int)Math.floor(screenMin.y),
        (int)(Math.ceil(screenMax.x) - x),
        (int)(Math.ceil(screenMax.y) - y));

    Bitmap image = imageLoader.getImage(url, mimeType, targetRect.width(), targetRect.height(), new ImageLoader.Observer()
    {
      @Override
      public void ready(String url, Bitmap image)
      {
        // image was not ready but is now so repaint the target component to get it displayed

        target.invalidate(imageLoader.getEditor().getRenderer(), targetRect.left, targetRect.top, targetRect.width(), targetRect.height(), EnumSet.allOf(IRenderTarget.LayerType.class));
      }
    });

    if (image == null)
    {
      // image is not ready yet...
      if (fillPaint.getColor() != android.graphics.Color.TRANSPARENT)
      {
        canvas.drawRect(x, y, width, height, fillPaint);
      }
    }
    else
    {
      // adjust rectangle so that the image gets fit into original rectangle
      float fx = (float)(width / image.getWidth());
      float fy = (float)(height / image.getHeight());
      if (fx > fy)
      {
        float w = (float)(image.getWidth() * fy);
        x += (width - w) / 2;
        width = w;
      }
      else
      {
        float h = (float)(image.getHeight() * fx);
        y += (height - h) / 2;
        height = h;
      }

      // draw the image
      Rect srcRect = new Rect(0, 0, image.getWidth(), image.getHeight());
      RectF dstRect = new RectF(x, y, x+width, y+height);
      canvas.drawBitmap(image, srcRect, dstRect, null);
    }
  }

  @Override
  public void drawText(@NonNull String label, float x, float y, float xmin, float ymin, float xmax, float ymax)
  {
    double scaleRatio = transform.xx / transform.yy;
    double dpiRatio = displayMetrics.xdpi / displayMetrics.ydpi;
    boolean stretched = !Numbers.isNear(scaleRatio, dpiRatio, 10e-3, 10e-5);

    // font size is scaled to the canvas transform scale, to ensure better font rendering
    // so here we only stretch the drawn text, if required
    if (stretched)
    {
      // stretch text to match the expected scale
      Matrix stretchMatrix = new Matrix();
      stretchMatrix.setScale((float)scaleRatio, 1.0f);
      canvas.setMatrix(stretchMatrix);
    }
    else
    {
      canvas.setMatrix(identityMatrix);
    }

    // transform only the insertion point
    Point p = transform.apply(x, y);

    if (stretched)
      p.x = p.x / (float)scaleRatio;

    // draw text
    canvas.drawText(label, p.x, p.y, textPaint);

    // restore transform
    canvas.setMatrix(transformMatrix);
  }
}
