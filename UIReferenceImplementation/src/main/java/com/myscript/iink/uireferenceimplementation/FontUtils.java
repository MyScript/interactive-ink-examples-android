// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.res.AssetManager;
import android.graphics.Typeface;

import com.myscript.iink.graphics.Style;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public final class FontUtils
{
  private FontUtils()
  {
    // Utility class
  }

  public static final int getTypefaceStyle(String fontStyle, String fontVariant, int fontWeight)
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

  public static final int getTypefaceStyle(Style style)
  {
    return getTypefaceStyle(style.getFontStyle(), style.getFontVariant(), style.getFontWeight());
  }

  public static final Typeface getTypeface(String fontFamily, int typefaceStyle)
  {
    return Typeface.create(fontFamily, typefaceStyle);
  }

  public static final Typeface getTypeface(String fontFamily, String fontStyle, String fontVariant, int fontWeight)
  {
    return getTypeface(fontFamily, getTypefaceStyle(fontStyle, fontVariant, fontWeight));
  }

  public static final Typeface getTypeface(Style style)
  {
    return getTypeface(style.getFontFamily(), getTypefaceStyle(style));
  }

  public static final Typeface getTypeface(Map<String, Typeface> typefaceMap, String fontFamily, String fontStyle, String fontVariant, int fontWeight)
  {
    Typeface ref = typefaceMap.get(fontFamily);

    if (ref == null)
      return getTypeface(fontFamily, getTypefaceStyle(fontStyle, fontVariant, fontWeight));

    return Typeface.create(ref, FontUtils.getTypefaceStyle(fontStyle, fontVariant, fontWeight));
  }

  public static final String getFontFamily(AssetManager assets, String fontPath)
  {
    InputStream in = null;
    try
    {
      in = assets.open(fontPath);
      int position = 0;

      byte[] header = new byte[4 + 2 * 4];
      if (in.read(header) != header.length)
        return null;
      position += header.length;

      // Read the version first
      int version = readDword(header, 0);

      // The version must be either 'true' (0x74727565) or 0x00010000 or 'OTTO' (0x4f54544f) for CFF style fonts.
      if ( version != 0x74727565 && version != 0x00010000 && version != 0x4f54544f)
        return null;

      // The TTF file consist of several sections called "tables", and we need to know how many of them are there.
      int numTables = readWord(header, 4);

      // Now we can read the tables
      for (int i = 0; i < numTables; i++)
      {
        // Read the table entry
        byte[] tableDescr = new byte[4 * 4];
        if (in.read(tableDescr) != tableDescr.length)
          return null;
        position += tableDescr.length;
        int tag = readDword(tableDescr, 0);

        // Now here' the trick. 'name' field actually contains the textual string name.
        // So the 'name' string in characters equals to 0x6E616D65
        if (tag == 0x6E616D65)
        {
          int offset = readDword(tableDescr, 8);
          int length = readDword(tableDescr, 12);

          // Here's the name section. Read it completely into the allocated buffer
          byte[] table = new byte[length];

          if (in.skip(offset - position) != offset - position)
            return null;
          if (in.read(table) != table.length)
            return null;

          // This is also a table. See http://developer.apple.com/fonts/ttrefman/rm06/Chap6name.html
          // According to Table 36, the total number of table records is stored in the second word, at the offset 2.
          // Getting the count and string offset - remembering it's big endian.
          int count = readWord(table, 2);
          int string_offset = readWord(table, 4);

          // Record starts from offset 6
          for (int record = 0; record < count; record++)
          {
            // Table 37 tells us that each record is 6 words -> 12 bytes, and that the nameID is 4th word so its offset is 6.
            // We also need to account for the first 6 bytes of the header above (Table 36), so...
            int nameID_offset = record * 12 + 6;
            int platformID = readWord(table, nameID_offset);
            int nameID_value = readWord(table, nameID_offset + 6);

            // Table 42 lists the valid name Identifiers. We're interested in 4 but not in Unicode encoding (for simplicity).
            // The encoding is stored as PlatformID and we're interested in Mac encoding
            if (nameID_value == 1 && platformID == 1)
            {
              // We need the string offset and length, which are the word 6 and 5 respectively
              int name_length = readWord(table, nameID_offset + 8);
              int name_offset = readWord(table, nameID_offset + 10);

              // The real name string offset is calculated by adding the string_offset
              name_offset = name_offset + string_offset;

              // Make sure it is inside the array
              if (name_offset >= 0 && name_offset + name_length < table.length)
              { return new String(table, name_offset, name_length); }
            }
          }
          // we jumped table's data so we can't continue reading table descriptors
          break;
        }
      }

      return null;
    }
    catch (IOException e)
    {
      // Most likely a corrupted font file
      return null;
    }
    finally
    {
      if (in != null) try { in.close(); } catch (IOException e) {}
    }
  }

  private static final int readWord(byte[] array, int offset)
  {
    int b1 = array[offset] & 0xFF;
    int b2 = array[offset + 1] & 0xFF;

    return b1 << 8 | b2;
  }

  private static final int readDword(byte[] array, int offset)
  {
    int b1 = array[offset] & 0xFF;
    int b2 = array[offset + 1] & 0xFF;
    int b3 = array[offset + 2] & 0xFF;
    int b4 = array[offset + 3] & 0xFF;

    return b1 << 24 | b2 << 16 | b3 << 8 | b4;
  }
}
