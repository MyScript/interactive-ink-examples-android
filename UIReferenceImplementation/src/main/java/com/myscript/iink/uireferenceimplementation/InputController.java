// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.Context;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.myscript.iink.ContentBlock;
import com.myscript.iink.Editor;
import com.myscript.iink.IRenderTarget;
import com.myscript.iink.PointerEvent;
import com.myscript.iink.PointerEventType;
import com.myscript.iink.PointerType;
import com.myscript.iink.graphics.Point;

import java.util.EnumSet;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.GestureDetectorCompat;

public class InputController implements View.OnTouchListener, GestureDetector.OnGestureListener
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

  private final IRenderTarget renderTarget;
  private final Editor editor;
  private int _inputMode;
  private final GestureDetectorCompat gestureDetector;
  private IInputControllerListener _listener;
  private final long eventTimeOffset;
  @VisibleForTesting
  public PointerType iinkPointerType;
  private ViewListener _viewListener;

  public InputController(Context context, IRenderTarget renderTarget, Editor editor)
  {
    this.renderTarget = renderTarget;
    this.editor = editor;
    _listener = null;
    _inputMode = INPUT_MODE_AUTO;
    gestureDetector = new GestureDetectorCompat(context, this);

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

  public final synchronized IInputControllerListener getListener()
  {
    return _listener;
  }

  private boolean handleOnTouchForPointer(MotionEvent event, int actionMask, int pointerIndex)
  {
    final int pointerId = event.getPointerId(pointerIndex);
    final int pointerType = event.getToolType(pointerIndex);

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
          {
            iinkPointerType = PointerType.ERASER;
          }
          else
          {
            iinkPointerType = PointerType.PEN;
          }
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

    if (iinkPointerType == PointerType.TOUCH)
    {
      gestureDetector.onTouchEvent(event);
    }

    int historySize = event.getHistorySize();

    switch (actionMask)
    {
      case MotionEvent.ACTION_POINTER_DOWN:
      case MotionEvent.ACTION_DOWN:
        editor.pointerDown(event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
        return true;

      case MotionEvent.ACTION_MOVE:
        if (historySize > 0)
        {
          PointerEvent[] pointerEvents = new PointerEvent[historySize + 1];
          for (int i = 0; i < historySize; ++i)
            pointerEvents[i] = new PointerEvent(PointerEventType.MOVE, event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i), eventTimeOffset + event.getHistoricalEventTime(i), event.getHistoricalPressure(pointerIndex, i), iinkPointerType, pointerId);
          pointerEvents[historySize] = new PointerEvent(PointerEventType.MOVE, event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
          editor.pointerEvents(pointerEvents, true);
        }
        else
        {
          editor.pointerMove(event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
        }
        return true;

      case MotionEvent.ACTION_POINTER_UP:
      case MotionEvent.ACTION_UP:
        if (historySize > 0)
        {
          PointerEvent[] pointerEvents = new PointerEvent[historySize];
          for (int i = 0; i < historySize; ++i)
            pointerEvents[i] = new PointerEvent(PointerEventType.MOVE, event.getHistoricalX(pointerIndex, i), event.getHistoricalY(pointerIndex, i), eventTimeOffset + event.getHistoricalEventTime(i), event.getHistoricalPressure(pointerIndex, i), iinkPointerType, pointerId);
          editor.pointerEvents(pointerEvents, true);
        }
        editor.pointerUp(event.getX(pointerIndex), event.getY(pointerIndex), eventTimeOffset + event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
        return true;

      case MotionEvent.ACTION_CANCEL:
        editor.pointerCancel(pointerId);
        return true;

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
    if (editor.isScrollAllowed())
    {
      Point oldOffset = editor.getRenderer().getViewOffset();
      Point newOffset = new Point(oldOffset.x + distanceX, oldOffset.y + distanceY);
      editor.clampViewOffset(newOffset);
      editor.getRenderer().setViewOffset(Math.round(newOffset.x), Math.round(newOffset.y));
      renderTarget.invalidate(editor.getRenderer(), EnumSet.allOf(IRenderTarget.LayerType.class));
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
}
