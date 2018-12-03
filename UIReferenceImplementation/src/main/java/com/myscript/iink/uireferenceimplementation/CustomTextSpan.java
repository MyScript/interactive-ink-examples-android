// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Parcel;
import android.text.TextPaint;
import android.text.style.TextAppearanceSpan;

public class CustomTextSpan extends TextAppearanceSpan
{

  public static final Creator<CustomTextSpan> CREATOR = new Creator<CustomTextSpan>()
  {
    @Override
    public CustomTextSpan createFromParcel(Parcel in)
    {
      return new CustomTextSpan(in);
    }

    @Override
    public CustomTextSpan[] newArray(int size)
    {
      return new CustomTextSpan[size];
    }
  };

  private Typeface mTypeface;

  public CustomTextSpan(Typeface tf, int style, int size, ColorStateList color, ColorStateList linkColor)
  {
    super("", style, size, color, linkColor);
    mTypeface = tf;
  }

  protected CustomTextSpan(Parcel in)
  {
    super(in);
  }

  @Override
  public int describeContents()
  {
    return 0;
  }

  @Override
  public void updateDrawState(TextPaint ds)
  {
    applyCustomTypeFace(ds);

    ColorStateList textColor = getTextColor();
    if (textColor != null)
    {
      ds.setColor(textColor.getColorForState(ds.drawableState, 0));
    }

    ColorStateList linkColor = getLinkTextColor();
    if (linkColor != null)
    {
      ds.linkColor = linkColor.getColorForState(ds.drawableState, 0);
    }
  }

  @Override
  public void updateMeasureState(TextPaint ds)
  {
    applyCustomTypeFace(ds);
  }

  private void applyCustomTypeFace(TextPaint ds)
  {

    int mStyle = getTextStyle();
    if (mTypeface != null || mStyle != 0)
    {
      Typeface tf = ds.getTypeface();
      int style = Typeface.NORMAL;

      if (tf != null)
      {
        style = tf.getStyle();
      }

      style |= mStyle;

      if (mTypeface != null)
      {
        tf = Typeface.create(mTypeface, style);
      }
      else if (tf == null)
      {
        tf = Typeface.defaultFromStyle(style);
      }
      else
      {
        tf = Typeface.create(tf, style);
      }

      int fake = style & ~tf.getStyle();

      if ((fake & Typeface.BOLD) != 0)
      {
        ds.setFakeBoldText(true);
      }

      if ((fake & Typeface.ITALIC) != 0)
      {
        ds.setTextSkewX(-0.25f);
      }

      ds.setTypeface(tf);
    }

    int mTextSize = getTextSize();
    if (mTextSize > 0)
    {
      ds.setTextSize(mTextSize);
    }
  }
}
