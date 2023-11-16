/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Adapted from:
// https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:input/input-motionprediction/

package com.myscript.iink.uireferenceimplementation;

import android.content.Context;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.DoNotInline;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Get screen fastest refresh rate (in ms)
 */
@SuppressWarnings("deprecation")
public class FrameTimeEstimator {
  private static final float LEGACY_FRAME_TIME_MS = 16f;
  private static final float MS_IN_A_SECOND = 1000f;

  static public float getFrameTime(@NonNull Context context)
  {
    return getFastestFrameTimeMs(context);
  }

  private static Display getDisplayForContext(Context context)
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    {
      return Api30Impl.getDisplayForContext(context);
    }
    return ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
  }

  private static float getFastestFrameTimeMs(Context context)
  {
    Display defaultDisplay = getDisplayForContext(context);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
    {
      return Api23Impl.getFastestFrameTimeMs(defaultDisplay);
    }
    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
    {
      return Api21Impl.getFastestFrameTimeMs(defaultDisplay);
    }
    else
    {
      return LEGACY_FRAME_TIME_MS;
    }
  }

  @SuppressWarnings("deprecation")
  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  static class Api21Impl
  {
    private Api21Impl()
    {
      // Not instantiable
    }

    @DoNotInline
    static float getFastestFrameTimeMs(Display display)
    {
      float[] refreshRates = display.getSupportedRefreshRates();
      float largestRefreshRate = refreshRates[0];

      for (int c = 1; c < refreshRates.length; c++)
      {
        if (refreshRates[c] > largestRefreshRate)
          largestRefreshRate = refreshRates[c];
      }

      return MS_IN_A_SECOND / largestRefreshRate;
    }
  }

  @RequiresApi(Build.VERSION_CODES.M)
  static class Api23Impl
  {
    private Api23Impl()
    {
      // Not instantiable
    }

    @DoNotInline
    static float getFastestFrameTimeMs(Display display)
    {
      Display.Mode[] displayModes = display.getSupportedModes();
      float largestRefreshRate = displayModes[0].getRefreshRate();

      for (int c = 1; c < displayModes.length; c++)
      {
        float currentRefreshRate = displayModes[c].getRefreshRate();
        if (currentRefreshRate > largestRefreshRate)
          largestRefreshRate = currentRefreshRate;
      }

      return MS_IN_A_SECOND / largestRefreshRate;
    }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  static class Api30Impl
  {
    private Api30Impl()
    {
      // Not instantiable
    }

    @DoNotInline
    static Display getDisplayForContext(Context context)
    {
      return context.getDisplay();
    }
  }
}
