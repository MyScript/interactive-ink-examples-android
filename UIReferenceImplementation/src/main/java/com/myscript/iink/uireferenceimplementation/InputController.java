// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.myscript.iink.ContentBlock;
import com.myscript.iink.Editor;
import com.myscript.iink.IRenderTarget;
import com.myscript.iink.PointerEvent;
import com.myscript.iink.PointerEventType;
import com.myscript.iink.PointerTool;
import com.myscript.iink.PointerType;
import com.myscript.iink.Renderer;
import com.myscript.iink.ToolController;
import com.myscript.iink.graphics.Point;

import java.util.EnumSet;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

public class InputController implements View.OnTouchListener, GestureDetector.OnGestureListener, ScaleGestureDetector.OnScaleGestureListener
{

  public interface ViewListener
  {
    void showScrollbars();
  }

  public static final int INPUT_MODE_NONE = -1;
  public static final int INPUT_MODE_FORCE_PEN = 0;
  public static final int INPUT_MODE_FORCE_TOUCH = 1;
  public static final int INPUT_MODE_AUTO = 2;
  public static final int INPUT_MODE_ERASER = 3;

  private static final float SCALING_SENSIBILITY = 1.5f;
  private static final float SCALING_THRESHOLD = 0.02f;

  private final EditorView editorView;
  private final Editor editor;
  private int _inputMode;
  private final GestureDetector gestureDetector;
  private final ScaleGestureDetector scaleGestureDetector;
  private IInputControllerListener _listener;
  private final long eventTimeOffset;
  @VisibleForTesting
  public PointerType iinkPointerType;
  private ViewListener _viewListener;

  private boolean isScalingEnabled = false;
  private float getPreviousScalingSpan;
  private float previousScalingFocusX;
  private float previousScalingFocusY;
  private boolean isMultiFingerTouch = false;
  private int previousPointerId;

  private boolean isScrollingEnabled = true;

  public InputController(Context context, EditorView editorView, Editor editor)
  {
    this.editorView = editorView;
    this.editor = editor;
    _listener = null;
    _inputMode = INPUT_MODE_AUTO;
    scaleGestureDetector = new ScaleGestureDetector(context, this);
    gestureDetector = new GestureDetector(context, this);

    long rel_t = SystemClock.uptimeMillis();
    long abs_t = System.currentTimeMillis();
    eventTimeOffset = abs_t - rel_t;
  }

  public final synchronized void setInputMode(int inputMode)
  {
    this._inputMode = inputMode;
  }

  public final synchronized int getInputMode()
  {
    return _inputMode;
  }

  public final synchronized void setViewListener(ViewListener listener)
  {
    this._viewListener = listener;
  }

  public final synchronized void setListener(IInputControllerListener listener)
  {
    this._listener = listener;
  }

  public final synchronized void setScalingEnabled(boolean enabled)
  {
    isScalingEnabled = enabled;
  }

  public final synchronized void setScrollingEnabled(boolean enabled) {
    isScrollingEnabled = enabled;
  }

  public final synchronized IInputControllerListener getListener()
  {
    return _listener;
  }

  public final synchronized int getPreviousPointerId()
  {
    return previousPointerId;
  }

  private boolean handleOnTouchForPointer(MotionEvent event, int actionMask, int pointerIndex)
  {
    final int pointerId = event.getPointerId(pointerIndex);
    final int pointerType = event.getToolType(pointerIndex);
    final int historySize = event.getHistorySize();
    final boolean useTiltInfo = pointerType == MotionEvent.TOOL_TYPE_STYLUS;

    int inputMode = getInputMode();
    if (inputMode == INPUT_MODE_FORCE_PEN)
    {
      iinkPointerType = PointerType.PEN;
    }
    else if (inputMode == INPUT_MODE_FORCE_TOUCH)
    {
      iinkPointerType = PointerType.TOUCH;
    }
    else
    {
      switch (pointerType)
      {
        case MotionEvent.TOOL_TYPE_STYLUS:
          if (inputMode == INPUT_MODE_ERASER)
            iinkPointerType = PointerType.ERASER;
          else
            iinkPointerType = PointerType.PEN;
          break;
        case MotionEvent.TOOL_TYPE_FINGER:
        case MotionEvent.TOOL_TYPE_MOUSE:
          iinkPointerType = PointerType.TOUCH;
          break;
        default:
          // unsupported event type
          return false;
      }
    }

    if (isScalingEnabled)
      scaleGestureDetector.onTouchEvent(event);

    if (iinkPointerType == PointerType.TOUCH)
      gestureDetector.onTouchEvent(event);

    switch (actionMask)
    {
      // ACTION_POINTER_DOWN is "A non-primary pointer has gone down", only called when a pointer is already on the touchscreen.
      case MotionEvent.ACTION_POINTER_DOWN:
      {
        isMultiFingerTouch = true;
        if (previousPointerId != -1)
        {
          editor.pointerCancel(previousPointerId);
          previousPointerId = -1;
        }
        return true;
      }
      case MotionEvent.ACTION_DOWN:
      {
        previousPointerId = pointerId;
        isMultiFingerTouch = false;
        // Request unbuffered events for tools that require low capture latency
        ToolController toolController = editor.getToolController();
        PointerTool tool = toolController.getToolForType(iinkPointerType);
        if (tool == PointerTool.PEN || tool == PointerTool.HIGHLIGHTER)
          editorView.requestUnbufferedDispatch(event);

        try
        {
          if (useTiltInfo)
            editor.pointerDown(event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(),
                event.getPressure(), event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex), event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex), iinkPointerType, pointerId);
          else
            editor.pointerDown(event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
        }
        catch (UnsupportedOperationException e) {
          // Special case: pointerDown already called, discard previous and retry
          editor.pointerCancel(pointerId);
          if (useTiltInfo)
            editor.pointerDown(event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(),
                event.getPressure(), event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex), event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex), iinkPointerType, pointerId);
          else
            editor.pointerDown(event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
        }
        return true;
      }
      case MotionEvent.ACTION_MOVE:
      {
        if (isMultiFingerTouch)
          return true;

        if (historySize > 0)
        {
          PointerEvent[] pointerEvents = new PointerEvent[historySize + 1];
          if (useTiltInfo)
          {
            for (int i = 0; i < historySize; ++i)
            {
              pointerEvents[i] = new PointerEvent(PointerEventType.MOVE, event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i), eventTimeOffset + event.getHistoricalEventTime(i),
                  event.getHistoricalPressure(pointerIndex, i), event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, i), event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex, i), iinkPointerType, pointerId);
            }
            pointerEvents[historySize] = new PointerEvent(PointerEventType.MOVE, event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(),
                event.getPressure(), event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex), event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex), iinkPointerType, pointerId);
          }
          else
          {
            for (int i = 0; i < historySize; ++i)
            {
              pointerEvents[i] = new PointerEvent(PointerEventType.MOVE, event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i), eventTimeOffset + event.getHistoricalEventTime(i),
                  event.getHistoricalPressure(pointerIndex, i), iinkPointerType, pointerId);
            }
            pointerEvents[historySize] = new PointerEvent(PointerEventType.MOVE, event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
          }
          editor.pointerEvents(pointerEvents, true);
        }
        else // no history
        {
          if (useTiltInfo)
            editor.pointerMove(event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(),
                event.getPressure(), event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex), event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex), iinkPointerType, pointerId);
          else
            editor.pointerMove(event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
        }
        return true;
      }
      // ACTION_POINTER_UP is "A non-primary pointer has gone up", at least one finger is still on the touchscreen.
      case MotionEvent.ACTION_POINTER_UP:
      {
        return true;
      }
      case MotionEvent.ACTION_UP:
      {
        if (isMultiFingerTouch)
        {
          isMultiFingerTouch = false;
          return true;
        }
        if (historySize > 0)
        {
          PointerEvent[] pointerEvents = new PointerEvent[historySize];
          if (useTiltInfo)
          {
            for (int i = 0; i < historySize; ++i)
            {
              pointerEvents[i] = new PointerEvent(PointerEventType.MOVE, event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i), eventTimeOffset + event.getHistoricalEventTime(i),
                  event.getHistoricalPressure(pointerIndex, i), event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, i), event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex, i), iinkPointerType, pointerId);
            }
          }
          else
          {
            for (int i = 0; i < historySize; ++i)
            {
              pointerEvents[i] = new PointerEvent(PointerEventType.MOVE, event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i), eventTimeOffset + event.getHistoricalEventTime(i),
                  event.getHistoricalPressure(pointerIndex, i), iinkPointerType, pointerId);
            }
          }
          editor.pointerEvents(pointerEvents, true);
        }
        if (useTiltInfo)
          editor.pointerUp(event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(),
              event.getPressure(), event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex), event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex), iinkPointerType, pointerId);
        else
          editor.pointerUp(event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);

        return true;
      }
      case MotionEvent.ACTION_CANCEL:
      {
        editor.pointerCancel(pointerId);
        return true;
      }
      default:
        return false;
    }
  }

  @Override
  public boolean onTouch(View v, MotionEvent event)
  {
    if (editor == null)
    {
      return false;
    }

    final int action = event.getAction();
    final int actionMask = action & MotionEvent.ACTION_MASK;

    try
    {
      if (actionMask == MotionEvent.ACTION_POINTER_DOWN || actionMask == MotionEvent.ACTION_POINTER_UP)
      {
        final int pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        return handleOnTouchForPointer(event, actionMask, pointerIndex);
      }
      else
      {
        boolean consumed = false;
        final int pointerCount = event.getPointerCount();
        for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex++)
        {
          try
          {
            consumed = consumed || handleOnTouchForPointer(event, actionMask, pointerIndex);
          }
          catch(Exception e) {
            // ignore spurious invalid touch events that may occurs when spamming undo/redo button
          }
        }
        return consumed;
      }
    }
    catch(UnsupportedOperationException e) {
      // such an error may be generated by monkey tests
      Log.e("InputController", "bad touch sequence", e);
      return false;
    }
  }

  @Override
  public boolean onDown(MotionEvent event)
  {
    return false;
  }

  @Override
  public void onShowPress(MotionEvent event)
  {
    // no-op
  }

  @Override
  public boolean onSingleTapUp(MotionEvent event)
  {
    return false;
  }

  @Override
  public void onLongPress(MotionEvent event)
  {
    IInputControllerListener listener = getListener();
    if (listener != null)
    {
      final float x = event.getX();
      final float y = event.getY();
      // Only handle block ID and not `ContentBlock` to simplify native `AutoCloseable` object lifecycle.
      // Otherwise, depending on which object owns such `ContentBlock`, the reasoning about closing it
      // would be more complicated.
      // Providing block ID delegates to listeners the ownership of the retrieved block (if any),
      // typically calling `Editor.getBlockById()`.
      try (@Nullable ContentBlock block = editor.hitBlock(x, y))
      {
        String blockId = block != null ? block.getId() : null;
        listener.onLongPress(x, y, blockId);
      }
    }
  }

  @Override
  public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
  {
    if (editor.isScrollAllowed() && isScrollingEnabled)
    {
      Point oldOffset = editor.getRenderer().getViewOffset();
      Point newOffset = new Point(oldOffset.x + distanceX, oldOffset.y + distanceY);
      editor.getRenderer().setViewOffset(Math.round(newOffset.x), Math.round(newOffset.y));
      editorView.invalidate(editor.getRenderer(), EnumSet.allOf(IRenderTarget.LayerType.class));
      if(_viewListener != null)
      {
        _viewListener.showScrollbars();
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
  {
    return false;
  }

  @Override
  public boolean onScale(ScaleGestureDetector scaleGestureDetector)
  {
    Renderer renderer = editorView.getRenderer();

    // Store the current focus of the scaleGestureDetector
    float currentScalingFocusX = scaleGestureDetector.getFocusX();
    float currentScalingFocusY = scaleGestureDetector.getFocusY();
    float currentSpan = scaleGestureDetector.getCurrentSpan();

    // Measure the delta of the currentFocus to the previous
    float distanceX = previousScalingFocusX - currentScalingFocusX;
    float distanceY = previousScalingFocusY - currentScalingFocusY;

    previousScalingFocusX = currentScalingFocusX;
    previousScalingFocusY = currentScalingFocusY;

    Point oldOffset = renderer.getViewOffset();
    Point newOffset = new Point(oldOffset.x + distanceX, oldOffset.y + distanceY);

    // Apply the translation of the scaling focus to the render
    renderer.setViewOffset(Math.round(newOffset.x), Math.round(newOffset.y));

    float deltaSpan = getPreviousScalingSpan / currentSpan;
    // Apply a ratio in order to avoid the scaling to move too fast
    deltaSpan = 1.0f + ((1.0f - deltaSpan) / SCALING_SENSIBILITY);

    // Do not move if the scaling is too small
    if (deltaSpan > (1 + SCALING_THRESHOLD) || deltaSpan < (1 - SCALING_THRESHOLD))
    {
      renderer.zoomAt(new Point(currentScalingFocusX, currentScalingFocusY), deltaSpan);
    }

    // Store the span for next time
    getPreviousScalingSpan = currentSpan;
    editorView.invalidate(renderer, EnumSet.allOf(IRenderTarget.LayerType.class));

    if(_viewListener != null)
    {
      _viewListener.showScrollbars();
    }
    return true;
  }

  @Override
  public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector)
  {
    getPreviousScalingSpan = scaleGestureDetector.getCurrentSpan();
    previousScalingFocusX = scaleGestureDetector.getFocusX();
    previousScalingFocusY = scaleGestureDetector.getFocusY();
    return true;
  }

  @Override
  public void onScaleEnd(ScaleGestureDetector scaleGestureDetector)
  {
    // no-op
  }
}
