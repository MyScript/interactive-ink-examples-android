// Copyright MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.myscript.iink.Configuration;
import com.myscript.iink.ContentBlock;
import com.myscript.iink.ContentPart;
import com.myscript.iink.Editor;
import com.myscript.iink.IEditorListener2;
import com.myscript.iink.IRendererListener;
import com.myscript.iink.MimeType;
import com.myscript.iink.ParameterSet;
import com.myscript.iink.Renderer;
import com.myscript.iink.graphics.Point;
import com.myscript.iink.graphics.Rectangle;
import com.myscript.iink.graphics.Transform;

import java.util.Arrays;

public class SmartGuideView extends LinearLayout implements IEditorListener2, IRendererListener, View.OnClickListener
{
  private static final String TAG = "SmartGuideView";
  private static final int SMART_GUIDE_FADE_OUT_DELAY_WRITE_IN_DIAGRAM_DEFAULT = 3000;
  private static final int SMART_GUIDE_FADE_OUT_DELAY_WRITE_DEFAULT = 0;
  private static final int SMART_GUIDE_FADE_OUT_DELAY_OTHER_DEFAULT = 0;
  private static final int SMART_GUIDE_HIGHLIGHT_REMOVAL_DELAY_DEFAULT = 2000;

  enum UpdateCause
  {
    /**
     * A visual change occurred.
     */
    VISUAL,

    /**
     * An edit occurred (writing or editing gesture).
     */
    EDIT,

    /**
     * The selection changed.
     */
    SELECTION,

    /**
     * View parameters changed (scroll or zoom).
     */
    VIEW
  }

  enum TextBlockStyle
  {
    H1,
    H2,
    H3,
    NORMAL
  }

  @Nullable
  private Editor editor;

  @Nullable
  private ParameterSet exportParams;

  @Nullable
  private ContentBlock activeBlock;
  @Nullable
  private ContentBlock selectedBlock;
  @Nullable
  private ContentBlock block;

  @Nullable
  private SmartGuideWord[] words;

  private Resources res;
  private float density;

  private int removeHighlightDelay;
  private int fadeOutWriteInDiagramDelay;
  private int fadeOutWriteDelay;
  private int fadeOutOtherDelay;
  private Handler fadeOutTimerHandler;
  private Runnable fadeOutTimerRunnable;

  @Nullable
  private IInputControllerListener smartGuideMoreHandler;

  private class SmartGuideWord
  {
    private String label;
    private String[] candidates;
    private boolean modified;

    public SmartGuideWord(JiixDefinitions.Word word)
    {
      label = word.label;
      candidates = word.candidates;
      modified = false;
    }
  }

  private class SmartGuideWordView extends AppCompatTextView implements View.OnClickListener
  {
    private SmartGuideWord word;
    private int index;
    private Handler removeHighlightTimerHandler;
    private Runnable removeHighlightTimerRunnable;

    public SmartGuideWordView(Context context)
    {
      this(context, null, 0);
    }

    public SmartGuideWordView(Context context, AttributeSet attrs)
    {
      this(context, attrs, 0);
    }

    public SmartGuideWordView(Context context, AttributeSet attrs, int defStyleAttr)
    {
      super(context, attrs, defStyleAttr);
      setOnClickListener(this);
      word = null;
      index = -1;

      removeHighlightTimerHandler = new Handler();
      removeHighlightTimerRunnable = new Runnable()
      {
        @Override
        public void run()
        {
          setTextColor(res.getColor(R.color.word_gray));
        }
      };
    }

    private void init(SmartGuideWord word, int index)
    {
      this.word = word;
      this.index = index;
      float textSizeInPixels = res.getDimension(R.dimen.smart_guide_text_size);
      int textSize = (int) (textSizeInPixels / density);
      setTextSize(textSize);
      setText(word.label.equals("\n") ? " " : word.label);
      if (word.modified)
      {
        setTextColor(Color.BLACK);
        removeHighlightTimerHandler.postDelayed(removeHighlightTimerRunnable, removeHighlightDelay);
      }
      else
        setTextColor(res.getColor(R.color.word_gray));
    }

    private boolean updateWord(int index, String label)
    {
      String jiixString = null;
      try
      {
        jiixString = editor.export_(block, MimeType.JIIX, exportParams);
      }
      catch (Exception e)
      {
        // no-op
      }
      Gson gson = new Gson();
      boolean ok = false;
      try
      {
        JsonObject result = gson.fromJson(jiixString, JsonObject.class);
        if (result != null)
        {
          JsonArray words = result.getAsJsonArray(JiixDefinitions.Result.WORDS_FIELDNAME);
          JsonObject word = words.get(index).getAsJsonObject();
          word.addProperty(JiixDefinitions.Word.LABEL_FIELDNAME, label);
        }
        jiixString = gson.toJson(result);
        editor.import_(MimeType.JIIX, jiixString, block);
        ok = true;
      }
      catch (JsonSyntaxException e)
      {
        Log.e(TAG, "Failed to edit jiix word candidate: " + e.toString());

      }
      catch (IndexOutOfBoundsException e)
      {
        Log.e(TAG, "Failed to edit jiix word candidate: " + e.toString());
      }
      catch (IllegalStateException e)
      {
        Log.e(TAG, "Failed to edit jiix word candidate: " + e.toString());
      }
      return ok;
    }

    @Override
    public void onClick(View v)
    {
      fadeOutTimerHandler.removeCallbacks(fadeOutTimerRunnable);

      if (word.label.equals(" ") || word.label.equals("\n"))
        return;

      String[] candidates;
      int selectedCandidate = 0;

      if (word.candidates != null)
      {
        for (int i = 0; i < word.candidates.length; ++i)
        {
          String candidate = word.candidates[i];
          if (candidate.equals(word.label))
            selectedCandidate = i;
        }
        candidates = word.candidates;
      }
      else
      {
        candidates = new String[1];
        candidates[0] = word.label;
      }

      final int selected = selectedCandidate;
      AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(v.getContext());
      dialogBuilder.setSingleChoiceItems(candidates, selected, new DialogInterface.OnClickListener()
      {
        @Override
        public void onClick(DialogInterface dialog, int checked)
        {
          if (word.candidates != null)
          {
            String newLabel = word.candidates[checked];
            if (checked != selected)
            {
              if (updateWord(index, newLabel))
              {
                setText(newLabel);
                word.label = newLabel;
              }
              else
              {
                update(null, UpdateCause.EDIT);
              }
            }
          }
          dialog.dismiss();
        }
      });
      dialogBuilder.show();
    }
  }

  public SmartGuideView(Context context)
  {
    this(context, null, 0);
  }

  public SmartGuideView(Context context, @Nullable AttributeSet attrs)
  {
    this(context, attrs, 0);
  }

  public SmartGuideView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);

    activeBlock = null;
    words = null;
    smartGuideMoreHandler = null;
  }

  public void setEditor(@NonNull Editor editor)
  {
    this.editor = editor;
    editor.addListener(this);
    editor.getRenderer().addListener(this);

    this.exportParams = editor.getEngine().createParameterSet();
    this.exportParams.setBoolean("export.jiix.strokes", false);
    this.exportParams.setBoolean("export.jiix.bounding-box", false);
    this.exportParams.setBoolean("export.jiix.glyphs", false);
    this.exportParams.setBoolean("export.jiix.primitives", false);
    this.exportParams.setBoolean("export.jiix.chars", false);

    Configuration configuration = editor.getEngine().getConfiguration();
    fadeOutWriteInDiagramDelay = configuration.getNumber("smart-guide.fade-out-delay.write-in-diagram", SMART_GUIDE_FADE_OUT_DELAY_WRITE_IN_DIAGRAM_DEFAULT).intValue();
    fadeOutWriteDelay = configuration.getNumber("smart-guide.fade-out-delay.write", SMART_GUIDE_FADE_OUT_DELAY_WRITE_DEFAULT).intValue();
    fadeOutOtherDelay = configuration.getNumber("smart-guide.fade-out-delay.other", SMART_GUIDE_FADE_OUT_DELAY_OTHER_DEFAULT).intValue();
    removeHighlightDelay = configuration.getNumber("smart-guide.highlight-removal-delay", SMART_GUIDE_HIGHLIGHT_REMOVAL_DELAY_DEFAULT).intValue();
  }

  public void setSmartGuideMoreHandler(IInputControllerListener smartGuideMoreHandler)
  {
    this.smartGuideMoreHandler = smartGuideMoreHandler;
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();

    res = getResources();
    density = res.getDisplayMetrics().density;

    TextView moreView = findViewById(R.id.more_view);
    moreView.setOnClickListener(this);
    if (smartGuideMoreHandler == null)
      moreView.setVisibility(View.GONE);

    fadeOutTimerHandler = new Handler();
    fadeOutTimerRunnable = new Runnable()
    {
      @Override
      public void run()
      {
        setVisibility(View.INVISIBLE);
      }
    };
  }

  @Override
  public void partChanging(Editor editor, ContentPart oldPart, ContentPart newPart)
  {
    // no-op
  }

  @Override
  public void partChanged(Editor editor)
  {
    activeBlock = selectedBlock = null;
    update(null, UpdateCause.VISUAL);
  }

  @Override
  public void contentChanged(Editor editor, String[] blockIds)
  {
    // The active block may have been removed then added again in which case
    // the old instance is invalid but can be restored by remapping the identifier
    if (activeBlock != null && !activeBlock.isValid())
    {
      activeBlock = editor.getBlockById(activeBlock.getId());
      if (activeBlock == null)
      {
        update(null, UpdateCause.EDIT);
        return;
      }
    }

    if (activeBlock != null && Arrays.asList(blockIds).contains(activeBlock.getId()))
    {
      if (block == null)
        block = activeBlock;
      update(activeBlock, UpdateCause.EDIT);
    }
  }

  @Override
  public void onError(Editor editor, String blockId, String message)
  {
    Log.e(TAG, "Failed to edit block \"" + blockId + "\": " + message);
  }

  @Override
  public void selectionChanged(Editor editor, String[] blockIds)
  {
    selectedBlock = null;
    for (int i = 0, n = blockIds.length; i < n; ++i)
    {
      ContentBlock block = editor.getBlockById(blockIds[i]);
      if (block != null && block.getType().equals("Text"))
      {
        selectedBlock = block;
        break;
      }
    }
    update(selectedBlock, UpdateCause.SELECTION);
  }

  @Override
  public void activeBlockChanged(Editor editor, String blockId)
  {
    activeBlock = editor.getBlockById(blockId);
    if (block != null && block.getId().equals(blockId))
      return; // selectionChanged already changed the active block

    update(activeBlock, UpdateCause.EDIT);
  }

  @Override
  public void viewTransformChanged(Renderer renderer)
  {
    update(block, UpdateCause.VIEW);
  }

  @Override
  public void onClick(View v)
  {
    if (v.getId() == R.id.more_view)
    {
      fadeOutTimerHandler.removeCallbacks(fadeOutTimerRunnable);

      if (smartGuideMoreHandler != null)
      {
        final float x = v.getX();
        final float y = v.getY();
        smartGuideMoreHandler.onLongPress(x, y, block);
      }
    }
  }

  private void update(ContentBlock block, final UpdateCause cause)
  {
    if (block != null && block.getType().equals("Text"))
    {
      Gson gson = new Gson();
      // Update size and position
      Rectangle rectangle = block.getBox();
      float paddingLeft = 0.0f;
      float paddingRight = 0.0f;
      if (block.getAttributes().length() > 0)
      {
        try
        {
          JiixDefinitions.Padding padding = gson.fromJson(block.getAttributes(), JiixDefinitions.Padding.class);
          if (padding != null)
          {
            paddingLeft = padding.left;
            paddingRight = padding.right;
          }
        }
        catch (JsonSyntaxException e)
        {
          Log.e(TAG, "Failed to parse attributes as json: " + e.toString());
        }
      }
      Transform transform = editor.getRenderer().getViewTransform();
      Point left = transform.apply(rectangle.x + paddingLeft, rectangle.y);
      Point right = transform.apply(rectangle.x + rectangle.width - paddingRight, rectangle.y);

      float x = left.x;
      float y = left.y;
      float width = right.x - left.x;

      TextView styleView;
      final HorizontalScrollView scrollView;
      TextView moreView;
      try
      {
        styleView = findViewById(R.id.style_view);
        scrollView = findViewById(R.id.scroll_view);
        moreView = findViewById(R.id.more_view);
      }
      catch(NullPointerException e)
      {
        Log.e(TAG, "Failed to access views :" + e.toString());
        return;
      }

      final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
      layoutParams.leftMargin = (int) x;
      layoutParams.topMargin = (int) y - getHeight();
      final LinearLayout.LayoutParams scrollViewLayoutParams = (LinearLayout.LayoutParams) scrollView.getLayoutParams();
      scrollViewLayoutParams.width = (int) width - styleView.getWidth() - moreView.getWidth();

      // Update words
      final SmartGuideWord[] updatedWords;
      boolean isSameActiveBlock = this.block != null && block.getId().equals(this.block.getId());
      if (cause != UpdateCause.EDIT && isSameActiveBlock)
      {
        // Nothing changed so keep same words
        updatedWords = this.words;
      }
      else
      {
        // Build new word list from JIIX export
        String jiixString;
        try
        {
          jiixString = editor.export_(block, MimeType.JIIX, this.exportParams);
        }
        catch (Exception e)
        {
          return; // when processing is ongoing, export may fail: ignore
        }
        SmartGuideWord[] smartGuideWords = null;
        try
        {
          JiixDefinitions.Result result = gson.fromJson(jiixString, JiixDefinitions.Result.class);
          if (result != null && result.words != null)
          {
            int count = result.words.length;
            smartGuideWords = new SmartGuideWord[count];
            for (int i = 0; i < count; ++i)
              smartGuideWords[i] = new SmartGuideWord(result.words[i]);
          }
        }
        catch (JsonSyntaxException e)
        {
          Log.e(TAG, "Failed to parse jiix string as json words: " + e.toString());
        }
        updatedWords = smartGuideWords;

        // Possibly compute difference with previous state
        if (isSameActiveBlock)
        {
          computeModificationOfWords(updatedWords, words);
        }
        else if (cause == UpdateCause.EDIT && updatedWords != null)
        {
          for (int i = 0; i < updatedWords.length; ++i)
            updatedWords[i].modified = true;
        }
      }

      final boolean updateWords = this.words != updatedWords;
      final boolean isInDiagram = block.getId().startsWith("diagram/");

      post(new Runnable()
      {
        @Override
        public void run()
        {
          setLayoutParams(layoutParams);
          scrollView.setLayoutParams(scrollViewLayoutParams);

          setTextBlockStyle(TextBlockStyle.NORMAL);

          if (updateWords)
          {
            final LinearLayout stackView = findViewById(R.id.stack_view);
            stackView.removeAllViews();
            SmartGuideWordView lastModifiedWordView_ = null;
            for (int i = 0; i < updatedWords.length; ++i)
            {
              SmartGuideWordView smartGuideWordView = new SmartGuideWordView(getContext());
              smartGuideWordView.init(updatedWords[i], i);
              stackView.addView(smartGuideWordView);
              if (smartGuideWordView.word.modified)
                lastModifiedWordView_ = smartGuideWordView;
            }
            if (lastModifiedWordView_ != null)
            {
              final SmartGuideWordView lastModifiedWordView = lastModifiedWordView_;
              scrollView.post(new Runnable()
              {
                public void run()
                {
                  Rect rect = new Rect();
                  lastModifiedWordView.getHitRect(rect); // coordinates of lastModifiedWordView relative to its parent stackView
                  scrollView.requestChildRectangleOnScreen(stackView, rect, false);
                }
              });
            }
          }

          int delay;
          if (cause == UpdateCause.EDIT)
          {
            if (isInDiagram)
              delay = fadeOutWriteInDiagramDelay;
            else
              delay = fadeOutWriteDelay;
          }
          else
            delay = fadeOutOtherDelay;

          if (cause != UpdateCause.VIEW)
          {
            fadeOutTimerHandler.removeCallbacks(fadeOutTimerRunnable);

            if (delay > 0)
              fadeOutTimerHandler.postDelayed(fadeOutTimerRunnable, delay);

            setVisibility(View.VISIBLE);
          }
        }
      });

      this.block = block;
      this.words = updatedWords;
    }
    else
    {
      fadeOutTimerHandler.removeCallbacks(fadeOutTimerRunnable);

      post(new Runnable()
      {
        @Override
        public void run()
        {
          setVisibility(View.INVISIBLE);
        }
      });

      this.block = null;
    }
  }

  private void computeModificationOfWords(SmartGuideWord[] words, SmartGuideWord[] oldWords)
  {
    int len1 = oldWords.length;
    int len2 = words.length;

    int[][] d = new int[len1 + 1][len2 + 1];
    int i;
    int j;

    // Levenshtein distance algorithm at word level
    d[0][0] = 0;
    for (i = 1; i <= len1; ++i)
      d[i][0] = i;
    for (i = 1; i <= len2; ++i)
      d[0][i] = i;

    for (i = 1; i <= len1; ++i)
    {
      for (j = 1; j <= len2; ++j)
      {
        int d1 = d[i - 1][j] + 1;
        int d2 = d[i][j - 1] + 1;
        int d3 = d[i - 1][j - 1] + (oldWords[i - 1].label.equals(words[j - 1].label) ? 0 : 1);
        d[i][j] = Math.min(Math.min(d1, d2), d3);
      }
    }

    // Backward traversal
    for (j = 0; j < len2; ++j)
      words[j].modified = true;

    if ((len1 > 0) && (len2 > 0))
    {
      i = len1;
      j = len2;

      while (j > 0)
      {
        int d01 = d[i][j - 1];
        int d11 = (i > 0) ? d[i - 1][j - 1] : -1;
        int d10 = (i > 0) ? d[i - 1][j] : -1;

        if ((d11 >= 0) && (d11 <= d10) && (d11 <= d01))
        {
          --i;
          --j;
        }
        else if ((d10 >= 0) && (d10 <= d11) && (d10 <= d01))
        {
          --i;
        }
        else //if ( (d01 <= d11) && (d01 <= d10) )
        {
          --j;
        }

        if ((i < len1) && (j < len2))
          words[j].modified = !oldWords[i].label.equals(words[j].label);
      }
    }
  }

  private void setTextBlockStyle(TextBlockStyle textBlockStyle)
  {
    TextView styleView = findViewById(R.id.style_view);
    switch (textBlockStyle)
    {
      case H1:
        styleView.setText(res.getString(R.string.style_view_string_h1));
        styleView.setTextColor(Color.WHITE);
        styleView.setBackgroundColor(Color.BLACK);
        break;
      case H2:
        styleView.setText(res.getString(R.string.style_view_string_h2));
        styleView.setTextColor(Color.WHITE);
        styleView.setBackgroundColor(res.getColor(R.color.control_gray));
        break;
      case H3:
        styleView.setText(res.getString(R.string.style_view_string_h3));
        styleView.setTextColor(Color.WHITE);
        styleView.setBackgroundColor(res.getColor(R.color.control_gray));
        break;
      case NORMAL:
      default:
        styleView.setText(res.getString(R.string.style_view_string_normal));
        styleView.setTextColor(res.getColor(R.color.control_gray));
        styleView.setBackground(res.getDrawable(R.drawable.rectangle_border));
        break;
    }
  }
}
