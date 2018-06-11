// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.text.style.TextAppearanceSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import com.myscript.iink.graphics.Rectangle;
import com.myscript.iink.graphics.Style;
import com.myscript.iink.text.GlyphMetrics;
import com.myscript.iink.text.IFontMetricsProvider2;
import com.myscript.iink.text.Text;
import com.myscript.iink.text.TextSpan;

import java.util.Map;

public class FontMetricsProvider implements IFontMetricsProvider2
{
  DisplayMetrics displayMetrics;
  private TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private Path charPath = new Path();
  private RectF charBox = new RectF();
  private Map<String, Typeface> typefaceMap;

  public FontMetricsProvider(DisplayMetrics displayMetrics, Map<String, Typeface> typefaceMap)
  {
    this.displayMetrics = displayMetrics;
    this.typefaceMap = typefaceMap;
  }

  private final float x_mm2px(float mm)
  {
    return (mm / 25.4f) * displayMetrics.xdpi;
  }

  private final float y_mm2px(float mm)
  {
    return (mm / 25.4f) * displayMetrics.ydpi;
  }

  private final float x_px2mm(float px)
  {
    return 25.4f * (px / displayMetrics.xdpi);
  }

  private final float y_px2mm(float px)
  {
    return 25.4f * (px / displayMetrics.ydpi);
  }

  private final int updatePaint(TextSpan[] spans, int glyphCount, int[] fontSizes, Typeface[] typefaces, int spanIndex)
  {
    paint.setTypeface(typefaces[spanIndex]);
    paint.setTextSize(fontSizes[spanIndex]);
    return spans.length > spanIndex + 1 ? spans[1].getBeginPosition() : glyphCount;
  }

  @Override
  public Rectangle[] getCharacterBoundingBoxes(Text text, TextSpan[] spans)
  {
    GlyphMetrics[] metrics = getGlyphMetrics(text, spans);
    Rectangle[] charBoxes = new Rectangle[metrics.length];
    for (int i = 0; i < charBoxes.length; ++i)
      charBoxes[i] = new Rectangle(metrics[i].boundingBox);
    return charBoxes;
  }

  @Override
  public float getFontSizePx(Style style)
  {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.getFontSize(), displayMetrics);
  }

  @Override
  public GlyphMetrics[] getGlyphMetrics(Text text, TextSpan[] spans)
  {
    String label = text.getLabel();

    // Create spannable string that represent text with spans (ignoring color)
    SpannableString string = new SpannableString(label);

    ColorStateList fontColor = ColorStateList.valueOf(Color.BLACK);
    ColorStateList fontLinkColor = null;

    int[] fontSizes = new int[spans.length];
    Typeface[] typefaces = new Typeface[spans.length];

    for (int i = 0; i < spans.length; i++)
    {
      Style style = spans[i].getStyle();

      int typefaceStyle = FontUtils.getTypefaceStyle(style);
      String fontFamily = style.getFontFamily();
      int fontSize = Math.round(y_mm2px(style.getFontSize()));

      int start = text.getGlyphBeginAt(spans[i].getBeginPosition());
      int end = text.getGlyphEndAt(spans[i].getEndPosition() - 1);

      MetricAffectingSpan span;
      Typeface typeface = FontUtils.getTypeface(typefaceMap, fontFamily, style.getFontStyle(), style.getFontVariant(), style.getFontWeight());
      if (typeface == null)
        span = new TextAppearanceSpan(fontFamily, typefaceStyle, fontSize, fontColor, fontLinkColor);
      else
        span = new CustomTextSpan(typeface, typefaceStyle, fontSize, fontColor, fontLinkColor);

      string.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

      fontSizes[i] = fontSize;
      typefaces[i] = typeface;
    }

    // Layout text
    Layout layout = new StaticLayout(string, paint, 100000, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    if (layout.getLineCount() != 1)
      throw new RuntimeException();

    // Get bounding boxes
    int glyphCount = text.getGlyphCount();

    // initialize style
    int spanIndex = 0;
    int nextSpan = updatePaint(spans, glyphCount, fontSizes, typefaces, spanIndex);

    GlyphMetrics[] charBoxes = new GlyphMetrics[glyphCount];

    float XHeight = 0;
    for (int i = 0; i < glyphCount; ++i)
    {
      if (i >= nextSpan)
      {
        ++spanIndex;
        nextSpan = updatePaint(spans, glyphCount, fontSizes, typefaces, spanIndex);
      }

      int start = text.getGlyphBeginAt(i);
      int end = text.getGlyphEndAt(i);
      float left;

      left = layout.getPrimaryHorizontal(start);
      paint.getTextPath(label, start, end, 0, 0, charPath);
      charPath.computeBounds(charBox, true);

      // some glyphs paths may not be available (like for emojis)
      // in that case we estimate the actual size of the glyph based on "X" height and primary horizontal values
      if (charBox.isEmpty() && !label.equals(" "))
      {
        if (XHeight == 0)
        {
          paint.getTextPath("X", 0, 1, 0, 0, charPath);
          charPath.computeBounds(charBox, true);
          XHeight = charBox.top;
        }

        charBox.left = 0;
        charBox.top = XHeight;
        charBox.right = layout.getPrimaryHorizontal(end) - left;
        charBox.bottom = 0;
      }

      float leftSideBearing = -x_px2mm(charBox.left);
      float x = x_px2mm(left) - leftSideBearing;
      float y = y_px2mm(charBox.top);
      float width = x_px2mm(charBox.width());
      float height = y_px2mm(charBox.height());

      charBoxes[i] = new GlyphMetrics(x, y, width, height, leftSideBearing, 0);
    }

    return charBoxes;
  }
}
