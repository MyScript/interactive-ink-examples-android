// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import com.google.gson.annotations.SerializedName;

/**
 * Class definition used for Gson parsing
 */
public class JiixDefinitions
{
  public static class Padding
  {
    public float left;
    public float right;
  }

  public static class Word
  {
    public static String LABEL_FIELDNAME = "label";
    public String label;
    public String[] candidates;
    @SerializedName(value = "reflow-label")
    public String reflowlabel;
  }

  public static class Result
  {
    public static String WORDS_FIELDNAME = "words";
    public Word[] words;
  }

}
