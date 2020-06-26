// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.collection.LruCache;
import androidx.core.util.Pair;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.text.style.TextAppearanceSpan;
import android.util.DisplayMetrics;

import com.myscript.iink.graphics.Rectangle;
import com.myscript.iink.graphics.Style;
import com.myscript.iink.text.GlyphMetrics;
import com.myscript.iink.text.IFontMetricsProvider2;
import com.myscript.iink.text.Text;
import com.myscript.iink.text.TextSpan;

import java.util.Map;

public class FontMetricsProvider implements IFontMetricsProvider2
{
  private static class FontKey
  {
    @NonNull
    final String family;
    final int style;
    final int size;

    public FontKey(@NonNull String family, int style, int size)
    {
      this.family = family;
      this.style = style;
      this.size = size;
    }

    @Override
    public int hashCode()
    {
      return family.hashCode() ^ style ^ size;
    }

    @Override
    public boolean equals(Object obj)
    {
      if (obj == this)
        return true;
      if (!(obj instanceof FontKey))
        return false;
      FontKey other = (FontKey) obj;
      return style == other.style && size == other.size && family.equals(other.family);
    }
  }

  private interface IValueProvider<T>
  {
    T get();
  }

  private DisplayMetrics displayMetrics;
  private TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private TextPaint paint_ = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private Path charPath = new Path();
  private RectF charBox = new RectF();
  private Map<String, Typeface> typefaceMap;

  private final LruCache<Pair<FontKey, String>, GlyphMetrics> glyphMetricsCache = new LruCache<>(1024);
  private final LruCache<Pair<FontKey, String>, Float> pairKerningCache = new LruCache<>(2048);

  public FontMetricsProvider(DisplayMetrics displayMetrics, Map<String, Typeface> typefaceMap)
  {
    this.displayMetrics = displayMetrics;
    this.typefaceMap = typefaceMap;
  }

  private float x_mm2px(float mm)
  {
    return (mm / 25.4f) * displayMetrics.xdpi;
  }

  private float y_mm2px(float mm)
  {
    return (mm / 25.4f) * displayMetrics.ydpi;
  }

  private float x_px2mm(float px)
  {
    return 25.4f * (px / displayMetrics.xdpi);
  }

  private float y_px2mm(float px)
  {
    return 25.4f * (px / displayMetrics.ydpi);
  }

  private void updatePaint(int[] fontSizes, Typeface[] typefaces, int spanIndex)
  {
    paint.setTypeface(typefaces[spanIndex]);
    paint.setTextSize(fontSizes[spanIndex]);
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
    return style.getFontSize() * displayMetrics.scaledDensity;
  }

  private GlyphMetrics getGlyphMetrics(FontKey fontKey, String glyph, IValueProvider<GlyphMetrics> valueProvider)
  {
    Pair<FontKey, String> key = new Pair<>(fontKey, glyph);
    synchronized (glyphMetricsCache)
    {
      GlyphMetrics metrics = glyphMetricsCache.get(key);
      if (metrics == null)
      {
        metrics = valueProvider.get();
        glyphMetricsCache.put(key, metrics);
      }
      return metrics;
    }
  }

  private float getPairKerning(FontKey fontKey, String glyph1, String glyph2, IValueProvider<Float> valueProvider)
  {
    Pair<FontKey, String> key = new Pair<>(fontKey, glyph1 + glyph2);
    synchronized (pairKerningCache)
    {
      Float value = pairKerningCache.get(key);
      if (value == null)
      {
        value = valueProvider.get();
        pairKerningCache.put(key, value);
      }
      return value;
    }
  }

  @Override
  public GlyphMetrics[] getGlyphMetrics(Text text, TextSpan[] spans)
  {
    final String label = text.getLabel();

    // Create spannable string that represent text with spans (ignoring color)
    final SpannableString string = new SpannableString(label);

    ColorStateList fontColor = ColorStateList.valueOf(Color.BLACK);
    ColorStateList fontLinkColor = null;

    int[] fontSizes = new int[spans.length];
    Typeface[] typefaces = new Typeface[spans.length];

    for (int i = 0; i < spans.length; i++)
    {
      Style style = spans[i].getStyle();

      String fontFamily = toPlatformFontFamily(style);

      int typefaceStyle = FontUtils.getTypefaceStyle(style);
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

    int glyphCount = text.getGlyphCount();
    GlyphMetrics[] charBoxes = new GlyphMetrics[glyphCount];

    // Layout text
    int spanEnd = -1;
    int spanIndex = -1;
    FontKey fontKey = null;
    float pos = 0;
    String glyph_ = null;
    final Layout[] layout = new Layout[1];
    for (int i = 0; i < glyphCount; ++i)
    {
      if (i >= spanEnd)
      {
        ++spanIndex;
        fontKey = new FontKey(toPlatformFontFamily(spans[spanIndex].getStyle()), typefaces[spanIndex].getStyle(), fontSizes[spanIndex]);
        spanEnd = spans[spanIndex].getEndPosition();
        updatePaint(fontSizes, typefaces, spanIndex);
      }

      final int start = text.getGlyphBeginAt(i);
      final int end = text.getGlyphEndAt(i);
      String glyph = label.substring(start, end);
      GlyphMetrics m = getGlyphMetrics(fontKey, glyph, new IValueProvider<GlyphMetrics>()
      {
        @Override
        public GlyphMetrics get()
        {
          paint.getTextPath(label, start, end, 0, 0, charPath);
          charPath.computeBounds(charBox, true);

          // some glyphs paths may not be available (like for emojis)
          // in that case we use simple text bounds, which are less precise but correct
          if (charBox.isEmpty() && !label.equals(" "))
          {
            Rect box = new Rect();
            paint.getTextBounds(label, start, end, box);
            charBox.left = box.left;
            charBox.top = box.top;
            charBox.right = box.right;
            charBox.bottom = box.bottom;
          }

          float x = x_px2mm(charBox.left);
          float y = y_px2mm(charBox.top);
          float width = x_px2mm(charBox.width());
          float height = y_px2mm(charBox.height());

          float leftSideBearing = -x;
          float rightSideBearing;
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
          {
            float advance = paint.getRunAdvance(label, start, end, start, end, false, end);
            rightSideBearing = x_px2mm(advance - charBox.right);
          }
          else
          {
            rightSideBearing = 0; // expect degraded reflow of typeset text
          }

          return new GlyphMetrics(x, y, width, height, leftSideBearing, rightSideBearing);
        }
      });

      if (i > 0)
      {
        float pairKerning;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (" ".equals(glyph_) || " ".equals(glyph)))
        {
          pairKerning = 0; // pair kerning is zero for space, when right side bearing is correct
        }
        else
        {
          final float defaultPos = pos;
          pairKerning = getPairKerning(fontKey, glyph_, glyph, new IValueProvider<Float>()
          {
            @Override
            public Float get()
            {
              if (layout[0] == null)
              {
                layout[0] = new StaticLayout(string, paint_, 100000, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (layout[0].getLineCount() != 1)
                  throw new RuntimeException();
              }
              float posPx = layout[0].getPrimaryHorizontal(start);
              float pos = x_px2mm(posPx);
              return pos - defaultPos;
            }
          });
        }
        pos += pairKerning;
      }

      charBoxes[i] = new GlyphMetrics(pos + m.boundingBox.x, m.boundingBox.y, m.boundingBox.width, m.boundingBox.height, m.leftSideBearing, m.rightSideBearing);

      glyph_ = glyph;
      pos += m.boundingBox.width - m.leftSideBearing + m.rightSideBearing;
    }

    return charBoxes;
  }

  public static final String toPlatformFontFamily(Style style)
  {
    return toPlatformFontFamily(style.getFontFamily(), style.getFontStyle());
  }

  public static final String toPlatformFontFamily(String fontFamily, String fontStyle)
  {
    return "STIXGeneral".equals(fontFamily) && "italic".equals(fontStyle) ? "STIX" : fontFamily;
  }
}
