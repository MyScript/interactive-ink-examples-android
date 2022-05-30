// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.util.Log;

import com.myscript.iink.graphics.Style;
import com.myscript.util.TTFAnalyzer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class FontUtils
{
  private FontUtils()
  {
    // utility class
  }

  public static Map<String, Typeface> loadFontsFromAssets(AssetManager assetManager)
  {
    return loadFontsFromAssets(assetManager, "fonts");
  }

  public static Map<String, Typeface> loadFontsFromAssets(AssetManager assetManager, String assetsDir)
  {
    Map<String, Typeface> typefaceMap = new HashMap<>();
    try
    {
      String[] files = assetManager.list(assetsDir);
      for (String filename : files)
      {
        if (!filename.endsWith(".ttf") && !filename.endsWith(".otf"))
          continue;

        String fontPath = assetsDir + File.separatorChar + filename;
        String fontFamily = FontUtils.getFontFamily(assetManager, fontPath);
        final Typeface typeface = Typeface.createFromAsset(assetManager, fontPath);
        if (fontFamily != null && typeface != null)
        {
          typefaceMap.put(fontFamily, typeface);
        }
      }
    }
    catch (IOException e)
    {
      Log.e("FontUtils", "Failed to list fonts from assets", e);
      return null;
    }
    return typefaceMap;
  }

  public static int getTypefaceStyle(String fontStyle, String fontVariant, int fontWeight)
  {
    // Looking at Typeface documentation we see that NORMAL = 0, BOLD = 1, ITALIC = 2, and
    // BOLD_ITALIC = 3, so Android font style is a simple BOLD and ITALIC bit flag combination:
    int typefaceStyle = Typeface.NORMAL;
    if (fontWeight >= 700)
      typefaceStyle |= Typeface.BOLD;
    if ("italic".equals(fontStyle))
      typefaceStyle |= Typeface.ITALIC;
    return typefaceStyle;
  }

  public static int getTypefaceStyle(Style style)
  {
    return getTypefaceStyle(style.getFontStyle(), style.getFontVariant(), style.getFontWeight());
  }

  public static Typeface getTypeface(String fontFamily, int typefaceStyle)
  {
    return Typeface.create(fontFamily, typefaceStyle);
  }

  public static Typeface getTypeface(String fontFamily, String fontStyle, String fontVariant, int fontWeight)
  {
    return getTypeface(fontFamily, getTypefaceStyle(fontStyle, fontVariant, fontWeight));
  }

  public static Typeface getTypeface(Style style)
  {
    return getTypeface(style.getFontFamily(), getTypefaceStyle(style));
  }

  public static Typeface getTypeface(Map<String, Typeface> typefaceMap, String fontFamily, String fontStyle, String fontVariant, int fontWeight)
  {
    Typeface ref = typefaceMap.get(fontFamily);

    if (ref == null)
      return getTypeface(fontFamily, getTypefaceStyle(fontStyle, fontVariant, fontWeight));

    return Typeface.create(ref, FontUtils.getTypefaceStyle(fontStyle, fontVariant, fontWeight));
  }

  public static String getFontFamily(AssetManager assets, String fontPath)
  {
    try (InputStream in = assets.open(fontPath))
    {
      return TTFAnalyzer.getTtfFontName(in);
    }
    catch (IOException e)
    {
      // Most likely a corrupted font file
      return null;
    }
  }
}
