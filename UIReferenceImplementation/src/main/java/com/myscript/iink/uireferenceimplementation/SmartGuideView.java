// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.uireferenceimplementation;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.myscript.iink.Configuration;
import com.myscript.iink.ContentBlock;
import com.myscript.iink.ContentPart;
import com.myscript.iink.ContentSelection;
import com.myscript.iink.ContentSelectionMode;
import com.myscript.iink.Editor;
import com.myscript.iink.EditorError;
import com.myscript.iink.Engine;
import com.myscript.iink.IEditorListener;
import com.myscript.iink.IRendererListener;
import com.myscript.iink.MimeType;
import com.myscript.iink.ParameterSet;
import com.myscript.iink.Renderer;
import com.myscript.iink.graphics.Point;
import com.myscript.iink.graphics.Rectangle;
import com.myscript.iink.graphics.Transform;

import java.util.ArrayList;
import java.util.Arrays;

public class SmartGuideView extends LinearLayout implements IEditorListener, IRendererListener, View.OnClickListener
{

  public interface MenuListener
  {
    void onMoreMenuClicked(float x, float y, @NonNull String blockId);
  }

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

  @Nullable
  private Typeface wordViewTypeface;

  @Nullable
  private Editor editor;

  @Nullable
  private ParameterSet exportParams;
  @Nullable
  private ParameterSet importParams;

  @Nullable
  private ContentBlock activeBlock;
  @Nullable
  private ContentBlock selectedBlock;

  @Nullable
  private SmartGuideWord[] words;

  private float density;

  private int removeHighlightDelay;
  private int fadeOutWriteInDiagramDelay;
  private int fadeOutWriteDelay;
  private int fadeOutOtherDelay;
  private Handler fadeOutTimerHandler;
  private Runnable fadeOutTimerRunnable;

  @Nullable
  private MenuListener moreMenuListener;

  private static class SmartGuideWord
  {
    private String label;
    private final String[] candidates;
    private boolean modified;

    public SmartGuideWord(JiixDefinitions.Word word)
    {
      label = word.reflowlabel == null ? word.label : word.reflowlabel;
      candidates = word.candidates;
      modified = false;
    }
  }

  private class SmartGuideWordView extends AppCompatTextView implements View.OnClickListener
  {
    private SmartGuideWord word;
    private int index;
    private final Handler removeHighlightTimerHandler;
    private final Runnable removeHighlightTimerRunnable;

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

      removeHighlightTimerHandler = new Handler(Looper.myLooper());
      removeHighlightTimerRunnable = () -> setTextColor(ResourcesCompat.getColor(getResources(), R.color.word_gray, context.getTheme()));
    }

    private void init(SmartGuideWord word, int index)
    {
      this.word = word;
      this.index = index;
      Resources resources = getResources();
      float textSizeInPixels = resources.getDimension(R.dimen.smart_guide_text_size);
      int textSize = (int) (textSizeInPixels / density);
      setTextSize(textSize);
      setText(word.label.equals("\n") ? " " : word.label);
      if (word.modified)
      {
        setTextColor(Color.BLACK);
        removeHighlightTimerHandler.postDelayed(removeHighlightTimerRunnable, removeHighlightDelay);
      }
      else
      {
        setTextColor(ResourcesCompat.getColor(resources, R.color.word_gray, getContext().getTheme()));
      }
    }

    private boolean updateWord(int index, String label)
    {
      Editor editor = SmartGuideView.this.editor;
      if (editor == null) return false;
      ContentBlock block = getBlock();
      if (block == null) return false;
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
        editor.import_(MimeType.JIIX, jiixString, block, importParams);
        return true;
      }
      catch (JsonSyntaxException | IndexOutOfBoundsException | IllegalStateException e)
      {
        Log.e(TAG, "Failed to edit jiix word candidate", e);
        return false;
      }
    }

    @Override
    public void onClick(View v)
    {
      fadeOutTimerHandler.removeCallbacks(fadeOutTimerRunnable);

      if (!TextUtils.isGraphic(word.label))
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
      dialogBuilder.setSingleChoiceItems(candidates, selected, (dialog, checked) -> {
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
      });
      dialogBuilder.show();
    }
  }

  public SmartGuideView(Context context)
  {
    super(context, null, 0);
  }

  public SmartGuideView(Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs, 0);
  }

  public SmartGuideView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
  }

  public void setTypeface(@Nullable Typeface wordViewTypeface)
  {
    this.wordViewTypeface = wordViewTypeface;
    ViewGroup stackView = (ViewGroup) findViewById(R.id.smart_guide_stack_view);
    for (int i = 0 ; i < stackView.getChildCount(); ++i)
    {
      View child = stackView.getChildAt(i);
      if (child instanceof TextView)
      {
        ((TextView) child).setTypeface(wordViewTypeface);
      }
    }
  }

  public void setEditor(@Nullable Editor editor)
  {
    if (this.editor != null && !this.editor.isClosed())
    {
      this.editor.removeListener(this);
      this.editor.getRenderer().removeListener(this);
    }
    this.editor = editor;
    if (editor != null)
    {
      editor.addListener(this);
      editor.getRenderer().addListener(this);
      Engine engine = editor.getEngine();

      exportParams = engine.createParameterSet();
      exportParams.setBoolean("export.jiix.bounding-box", false);
      exportParams.setBoolean("export.jiix.glyphs", false);
      exportParams.setBoolean("export.jiix.primitives", false);
      exportParams.setBoolean("export.jiix.strokes", false);
      exportParams.setBoolean("export.jiix.text.chars", false);
      exportParams.setBoolean("export.jiix.text.lines", false);
      exportParams.setBoolean("export.jiix.text.spans", false);
      exportParams.setBoolean("export.jiix.text.structure", false);
      exportParams.setBoolean("export.jiix.text.words", true);

      importParams = engine.createParameterSet();
      importParams.setString("diagram.import.jiix.action", "update");
      importParams.setString("raw-content.import.jiix.action", "update");
      importParams.setString("text-document.import.jiix.action", "update");
      importParams.setString("text.import.jiix.action", "update");

      Configuration configuration = engine.getConfiguration();
      fadeOutWriteInDiagramDelay = configuration.getNumber("smart-guide.fade-out-delay.write-in-diagram", SMART_GUIDE_FADE_OUT_DELAY_WRITE_IN_DIAGRAM_DEFAULT).intValue();
      fadeOutWriteDelay = configuration.getNumber("smart-guide.fade-out-delay.write", SMART_GUIDE_FADE_OUT_DELAY_WRITE_DEFAULT).intValue();
      fadeOutOtherDelay = configuration.getNumber("smart-guide.fade-out-delay.other", SMART_GUIDE_FADE_OUT_DELAY_OTHER_DEFAULT).intValue();
      removeHighlightDelay = configuration.getNumber("smart-guide.highlight-removal-delay", SMART_GUIDE_HIGHLIGHT_REMOVAL_DELAY_DEFAULT).intValue();
    }
  }

  public void setMenuListener(@Nullable MenuListener moreMenuListener)
  {
    this.moreMenuListener = moreMenuListener;
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();

    density = getResources().getDisplayMetrics().density;

    View moreView = findViewById(R.id.smart_guide_more_view);
    moreView.setOnClickListener(this);
    if (moreMenuListener == null && !isInEditMode())
      moreView.setVisibility(View.GONE);

    fadeOutTimerHandler = new Handler(Looper.myLooper());
    fadeOutTimerRunnable = () -> setVisibility(View.INVISIBLE);
  }

  @Override
  protected void onDetachedFromWindow()
  {
    if (selectedBlock != null)
    {
      selectedBlock.close();
      selectedBlock = null;
    }
    if (activeBlock != null)
    {
      activeBlock.close();
      activeBlock = null;
    }
    super.onDetachedFromWindow();
  }

  @Override
  public void partChanging(@NonNull Editor editor, ContentPart oldPart, ContentPart newPart)
  {
    // no-op
  }

  @Override
  public void partChanged(@NonNull Editor editor)
  {
    if (selectedBlock != null)
    {
      selectedBlock.close();
      selectedBlock = null;
    }
    if (activeBlock != null)
    {
      activeBlock.close();
      activeBlock = null;
    }
    update(null, UpdateCause.VISUAL);
  }

  @Override
  public void contentChanged(@NonNull Editor editor, String[] blockIds)
  {
    // The active block may have been removed then added again in which case
    // the old instance is invalid but can be restored by remapping the identifier
    if (activeBlock != null && !activeBlock.isValid())
    {
      String activeBlockId = activeBlock.getId();
      ContentBlock newActiveBlock = editor.getBlockById(activeBlockId);
      if (newActiveBlock != null)
      {
        activeBlock.close();
        activeBlock = newActiveBlock;
      }
      else
      {
        update(null, UpdateCause.EDIT);
      }
    }

    if (activeBlock != null && Arrays.asList(blockIds).contains(activeBlock.getId()))
    {
      update(activeBlock, UpdateCause.EDIT);
    }
  }

  @Override
  public void onError(@NonNull Editor editor, @NonNull String blockId, @NonNull EditorError err, @NonNull String message)
  {
    Log.e(TAG, "Failed to edit block \"" + blockId + "\": " + message);
  }

  @Override
  public void selectionChanged(@NonNull Editor editor)
  {
    ContentBlock newSelectionBlock = null;
    ContentSelectionMode mode = editor.getSelectionMode();
    if (mode != ContentSelectionMode.NONE && mode != ContentSelectionMode.LASSO)
    {
      ContentSelection selection = editor.getSelection();

      String[] blockIds;
      if (selection.isValid())
        blockIds = editor.getIntersectingBlocks(selection);
      else
        blockIds = new String[]{};
      selection.close();

      for (String blockId : blockIds)
      {
        ContentBlock block = editor.getBlockById(blockId);
        if (block != null && block.getType().equals("Text"))
        {
          newSelectionBlock = block;
          break;
        }
        else if (block != null)
        {
          block.close();
        }
      }
    }

    update(newSelectionBlock, UpdateCause.SELECTION);

    if (selectedBlock != null)
    {
      selectedBlock.close();
    }
    selectedBlock = newSelectionBlock;
  }

  @Override
  public void activeBlockChanged(@NonNull Editor editor, @NonNull String blockId)
  {
    ContentBlock block = getBlock();
    if (block != null && blockId.equals(block.getId()))
    {
      // selectionChanged already changed the active block
      return;
    }

    ContentBlock newActiveBlock = editor.getBlockById(blockId);
    update(newActiveBlock, UpdateCause.EDIT);

    if (activeBlock != null)
    {
      activeBlock.close();
    }
    activeBlock = newActiveBlock;
  }

  @Override
  public void viewTransformChanged(@NonNull Renderer renderer)
  {
    update(getBlock(), UpdateCause.VIEW);
  }

  @Override
  public void onClick(View v)
  {
    if (v.getId() == R.id.smart_guide_more_view)
    {
      fadeOutTimerHandler.removeCallbacks(fadeOutTimerRunnable);

      ContentBlock block = getBlock();
      try
      {
        if (editor != null && block != null && moreMenuListener != null)
        {
          String blockId = block.getId();
          Rectangle box = block.getBox();
          Transform transform = editor.getRenderer().getViewTransform();
          Point center = transform.apply(box.x + (box.width / 2), box.y + (box.height / 2));
          // Do not move reference to `ContentBlock` outside this object to simplify
          // ownership of native `AutoCloseable` objects.
          moreMenuListener.onMoreMenuClicked(center.x, center.y, blockId);
        }
      }
      catch (Exception e)
      {
        // targeted block might have been destroyed in the meantime
        selectedBlock = null;
        activeBlock = null;
        update(null, UpdateCause.EDIT);
      }
    }
  }

  private void update(@Nullable ContentBlock block, final UpdateCause cause)
  {
    Editor editor = SmartGuideView.this.editor;
    if (isAttachedToWindow() && editor != null && block != null && block.isValid() && block.getType().equals("Text"))
    {
      Gson gson = new Gson();
      // Update size and position
      Rectangle rectangle = block.getBox();
      JiixDefinitions.Padding padding = null;
      if (block.getAttributes().length() > 0)
      {
        try
        {
          padding = gson.fromJson(block.getAttributes(), JiixDefinitions.Padding.class);
        }
        catch (JsonSyntaxException e)
        {
          Log.e(TAG, "Failed to parse attributes as json", e);
        }
      }
      final float paddingLeft = padding != null ? padding.left : 0.0f;
      final float paddingRight = padding != null ? padding.right : 0.0f;

      // Update words
      final SmartGuideWord[] updatedWords;
      ContentBlock currentBlock = getBlock();
      boolean isSameActiveBlock = currentBlock != null && currentBlock.getId().equals(block.getId());
      if (cause != UpdateCause.EDIT && isSameActiveBlock)
      {
        // Nothing changed so keep same words
        updatedWords = words;
      }
      else
      {
        // Build new word list from JIIX export
        String jiixString;
        try
        {
          jiixString = editor.export_(block, MimeType.JIIX, exportParams);
        }
        catch (Exception e)
        {
          return; // when processing is ongoing, export may fail: ignore
        }
        ArrayList<SmartGuideWord> smartGuideWords = new ArrayList<>();
        try
        {
          JiixDefinitions.Result result = gson.fromJson(jiixString, JiixDefinitions.Result.class);
          if (result != null && result.words != null)
          {
            int count = result.words.length;
            for (int i = 0; i < count; ++i)
              smartGuideWords.add(new SmartGuideWord(result.words[i]));
          }
        }
        catch (JsonSyntaxException e)
        {
          Log.e(TAG, "Failed to parse jiix string as json words", e);
        }
        updatedWords = new SmartGuideWord[smartGuideWords.size()];
        smartGuideWords.toArray(updatedWords);

        // Possibly compute difference with previous state
        if (isSameActiveBlock && words != null)
        {
          computeModificationOfWords(updatedWords, words);
        }
        else if (cause == UpdateCause.EDIT)
        {
          for (SmartGuideWord updatedWord : updatedWords)
          {
            updatedWord.modified = true;
          }
        }
      }

      final boolean updateWords = words != updatedWords;
      final boolean isInDiagram = block.getId().startsWith("diagram/");

      post(() -> {
        if (!isAttachedToWindow())
          return;
        Editor editor_ = SmartGuideView.this.editor;
        if (editor_ == null || editor_.isClosed())
          return;
        Renderer renderer_ = editor_.getRenderer();
        if (renderer_ == null || renderer_.isClosed())
          return;

        Transform transform = renderer_.getViewTransform();
        Point left = transform.apply(rectangle.x + paddingLeft, rectangle.y);
        Point right = transform.apply(rectangle.x + rectangle.width - paddingRight, rectangle.y);

        float x = left.x;
        float y = left.y;
        float width = right.x - left.x;

        final HorizontalScrollView scrollView= findViewById(R.id.smart_guide_scroll_view);
        View moreView = findViewById(R.id.smart_guide_more_view);
        if (scrollView == null || moreView == null)
        {
          Log.e(TAG, "Failed to access views");
          return;
        }

        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        layoutParams.leftMargin = (int) x;
        layoutParams.topMargin = (int) y - getHeight();
        final LinearLayout.LayoutParams scrollViewLayoutParams = (LinearLayout.LayoutParams) scrollView.getLayoutParams();
        scrollViewLayoutParams.width = (int) width - moreView.getWidth();

        setLayoutParams(layoutParams);
        scrollView.setLayoutParams(scrollViewLayoutParams);

        if (updateWords)
        {
          final LinearLayout stackView = findViewById(R.id.smart_guide_stack_view);
          stackView.removeAllViews();
          SmartGuideWordView lastModifiedWordView_ = null;
          for (int i = 0; i < updatedWords.length; ++i)
          {
            SmartGuideWordView smartGuideWordView = new SmartGuideWordView(getContext());
            smartGuideWordView.setTypeface(wordViewTypeface);
            smartGuideWordView.init(updatedWords[i], i);
            stackView.addView(smartGuideWordView);
            if (smartGuideWordView.word.modified)
              lastModifiedWordView_ = smartGuideWordView;
          }
          if (lastModifiedWordView_ != null)
          {
            final SmartGuideWordView lastModifiedWordView = lastModifiedWordView_;
            scrollView.post(() -> {
              Rect rect = new Rect();
              lastModifiedWordView.getHitRect(rect); // coordinates of lastModifiedWordView relative to its parent stackView
              scrollView.requestChildRectangleOnScreen(stackView, rect, false);
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
      });

      words = updatedWords;
    }
    else
    {
      fadeOutTimerHandler.removeCallbacks(fadeOutTimerRunnable);

      post(() -> setVisibility(View.INVISIBLE));
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

  /**
   * Return an alias <b>reference</b> to either activeBlock or selectedBlock, do not <code>close()</code> it.
   * It must be closed through activeBlock and selectedBlock.
   */
  @Nullable
  private ContentBlock getBlock()
  {
    return selectedBlock != null ? selectedBlock : activeBlock;
  }
}
