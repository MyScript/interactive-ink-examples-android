// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.content.Context;
import android.support.v4.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.myscript.iink.Editor;
import com.myscript.iink.IRenderTarget;
import com.myscript.iink.PointerEvent;
import com.myscript.iink.PointerEventType;
import com.myscript.iink.PointerType;
import com.myscript.iink.graphics.Point;

import java.util.EnumSet;

public class InputController implements View.OnTouchListener, GestureDetector.OnGestureListener
{
  public static final int INPUT_MODE_FORCE_PEN = 0;
  public static final int INPUT_MODE_FORCE_TOUCH = 1;
  public static final int INPUT_MODE_AUTO = 2;

  private EditorView editorView;
  private Editor editor;
  private int inputMode;
  private GestureDetectorCompat gestureDetector;
  private IInputControllerListener listener;

  public InputController(Context context, EditorView editorView)
  {
    this.editorView = editorView;
    this.editor = editorView.getEditor();
    this.listener = null;
    inputMode = INPUT_MODE_AUTO;
    gestureDetector = new GestureDetectorCompat(context, this);

    editorView.setOnTouchListener(this);
  }

  public final void setInputMode(int inputMode)
  {
    this.inputMode = inputMode;
  }

  public final int getInputMode() { return inputMode; }

  public final void setListener(IInputControllerListener listener)
  {
    this.listener = listener;
  }

  public final boolean handleOnTouchForPointer(MotionEvent event, int actionMask, int pointerIndex)
  {
    final int pointerId = event.getPointerId(pointerIndex);
    final int pointerType = event.getToolType(pointerIndex);

    PointerType iinkPointerType;
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

    if (iinkPointerType == PointerType.TOUCH)
    {
      gestureDetector.onTouchEvent(event);
    }

    int historySize = event.getHistorySize();

    switch (actionMask)
    {
      case MotionEvent.ACTION_POINTER_DOWN:
      case MotionEvent.ACTION_DOWN:
        editor.pointerDown(event.getX(pointerIndex), event.getY(pointerIndex), event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
        return true;

      case MotionEvent.ACTION_MOVE:
        if (historySize > 0)
        {
          PointerEvent[] pointerEvents = new PointerEvent[historySize + 1];
          for (int i = 0; i < historySize; ++i)
            pointerEvents[i] = new PointerEvent(PointerEventType.MOVE, event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i), event.getHistoricalPressure(i), iinkPointerType, pointerId);
          pointerEvents[historySize] = new PointerEvent(PointerEventType.MOVE, event.getX(pointerIndex), event.getY(pointerIndex), event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
          editor.pointerEvents(pointerEvents, true);
        }
        else
        {
          editor.pointerMove(event.getX(pointerIndex), event.getY(pointerIndex), event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
        }
        return true;

      case MotionEvent.ACTION_POINTER_UP:
      case MotionEvent.ACTION_UP:
        if (historySize > 0)
        {
          PointerEvent[] pointerEvents = new PointerEvent[historySize + 1];
          for (int i = 0; i < historySize; ++i)
            pointerEvents[i] = new PointerEvent(PointerEventType.MOVE, event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i), event.getHistoricalPressure(i), iinkPointerType, pointerId);
          pointerEvents[historySize] = new PointerEvent(PointerEventType.UP, event.getX(pointerIndex), event.getY(pointerIndex), event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
          editor.pointerEvents(pointerEvents, true);
        }
        else
        {
          editor.pointerUp(event.getX(pointerIndex), event.getY(pointerIndex), event.getEventTime(), event.getPressure(), iinkPointerType, pointerId);
        }
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
        boolean consumed_ = handleOnTouchForPointer(event, actionMask, pointerIndex);
        consumed = consumed || consumed_;
      }
      return consumed;
    }
  }

  @Override
  public boolean onDown(MotionEvent e)
  {
    return true;
  }

  @Override
  public void onShowPress(MotionEvent e)
  {
  }

  @Override
  public boolean onSingleTapUp(MotionEvent e)
  {
    return false;
  }

  @Override
  public void onLongPress(MotionEvent e)
  {
    final float x = e.getX();
    final float y = e.getY();
    if (listener != null)
      listener.onDisplayContextMenu(x, y, editor.hitBlock(x, y), editor.getSupportedAddBlockTypes());
  }

  @Override
  public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
  {
    if (editor.isScrollAllowed())
    {
      Point oldOffset = editor.getRenderer().getViewOffset();
      Point newOffset = new Point(oldOffset.x + distanceX, oldOffset.y + distanceY);
      editor.clampViewOffset(newOffset);
      editor.getRenderer().setViewOffset(newOffset.x, newOffset.y);
      editorView.invalidate(editor.getRenderer(), EnumSet.allOf(IRenderTarget.LayerType.class));
    }
    return true;
  }

  @Override
  public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
  {
    return true;
  }
}
