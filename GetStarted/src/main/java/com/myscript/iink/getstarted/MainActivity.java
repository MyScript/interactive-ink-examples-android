// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.getstarted;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.os.Bundle;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.myscript.iink.Configuration;
import com.myscript.iink.ContentPackage;
import com.myscript.iink.ContentPart;
import com.myscript.iink.ConversionState;
import com.myscript.iink.Editor;
import com.myscript.iink.EditorError;
import com.myscript.iink.Engine;
import com.myscript.iink.IEditorListener;
import com.myscript.iink.Renderer;
import com.myscript.iink.getstarted.databinding.MainActivityBinding;
import com.myscript.iink.uireferenceimplementation.EditorBinding;
import com.myscript.iink.uireferenceimplementation.EditorData;
import com.myscript.iink.uireferenceimplementation.EditorView;
import com.myscript.iink.uireferenceimplementation.FontUtils;
import com.myscript.iink.uireferenceimplementation.InputController;
import com.myscript.iink.uireferenceimplementation.SmartGuideView;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class MainActivity extends AppCompatActivity
{
  private static final String TAG = "MainActivity";

  private Engine engine;
  private ContentPackage contentPackage;
  private ContentPart contentPart;

  private EditorData editorData;
  private EditorView editorView;
  private SmartGuideView smartGuideView;

  private MainActivityBinding binding;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    ErrorActivity.installHandler(this);

    engine = IInkApplication.getEngine();

    // configure recognition
    Configuration conf = engine.getConfiguration();
    String confDir = "zip://" + getPackageCodePath() + "!/assets/conf";
    conf.setStringArray("configuration-manager.search-path", new String[]{confDir});
    String tempDir = getFilesDir().getPath() + File.separator + "tmp";
    conf.setString("content-package.temp-folder", tempDir);

    binding = MainActivityBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    editorView = findViewById(com.myscript.iink.uireferenceimplementation.R.id.editor_view);
    smartGuideView = findViewById(com.myscript.iink.uireferenceimplementation.R.id.smart_guide_view);

    // load fonts
    AssetManager assetManager = getApplicationContext().getAssets();
    Map<String, Typeface> typefaceMap = FontUtils.loadFontsFromAssets(assetManager);
    editorView.setTypefaces(typefaceMap);

    EditorBinding editorBinding = new EditorBinding(engine, typefaceMap);
    editorData = editorBinding.openEditor(editorView);

    Editor editor = editorData.getEditor();
    setMargins(editor, R.dimen.editor_horizontal_margin, R.dimen.editor_vertical_margin);
    editor.addListener(new IEditorListener()
    {
      @Override
      public void partChanging(@NonNull Editor editor, ContentPart oldPart, ContentPart newPart)
      {
        // no-op
      }

      @Override
      public void partChanged(@NonNull Editor editor)
      {
        invalidateOptionsMenu();
        invalidateIconButtons();
      }

      @Override
      public void contentChanged(@NonNull Editor editor, String[] blockIds)
      {
        invalidateOptionsMenu();
        invalidateIconButtons();
      }

      @Override
      public void onError(@NonNull Editor editor, @NonNull String blockId, @NonNull EditorError error, @NonNull String message)
      {
        Log.e(TAG, "Failed to edit block \"" + blockId + "\"" + message);
      }

      @Override
      public void selectionChanged(@NonNull Editor editor)
      {
        // no-op
      }

      @Override
      public void activeBlockChanged(@NonNull Editor editor, @NonNull String blockId)
      {
        // no-op
      }
    });

    smartGuideView.setEditor(editor);

    setInputMode(InputController.INPUT_MODE_FORCE_PEN); // If using an active pen, put INPUT_MODE_AUTO here

    String packageName = "File1.iink";
    File file = new File(getFilesDir(), packageName);
    try
    {
      contentPackage = engine.createPackage(file);
      // Choose type of content (possible values are: "Text Document", "Text", "Diagram", "Math", "Drawing" and "Raw Content")
      contentPart = contentPackage.createPart("Text Document");
    }
    catch (IOException | IllegalArgumentException e)
    {
      Log.e(TAG, "Failed to open package \"" + packageName + "\"", e);
    }

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null && contentPart != null)
    {
      actionBar.setTitle(getString(R.string.main_title, contentPart.getType()));
      actionBar.setSubtitle(R.string.app_name);
    }
    else
    {
      setTitle(R.string.app_name);
    }

    // wait for view size initialization before setting part
    editorView.post(() -> {
      Renderer renderer = editorView.getRenderer();
      if (renderer != null)
      {
        renderer.setViewOffset(0, 0);
        editorView.getRenderer().setViewScale(1);
        editorView.setVisibility(View.VISIBLE);
        editor.setPart(contentPart);
      }
    });

    binding.inputModeForcePenButton.setOnClickListener((v) -> setInputMode(InputController.INPUT_MODE_FORCE_PEN));
    binding.inputModeForceTouchButton.setOnClickListener((v) -> setInputMode(InputController.INPUT_MODE_FORCE_TOUCH));
    binding.inputModeAutoButton.setOnClickListener((v) -> setInputMode(InputController.INPUT_MODE_AUTO));
    binding.undoButton.setOnClickListener((v) -> editor.undo());
    binding.redoButton.setOnClickListener((v) -> editor.redo());
    binding.clearButton.setOnClickListener((v) -> editor.clear());

    invalidateIconButtons();
  }

  @Override
  protected void onDestroy()
  {
    binding.inputModeForcePenButton.setOnClickListener(null);
    binding.inputModeForceTouchButton.setOnClickListener(null);
    binding.inputModeAutoButton.setOnClickListener(null);
    binding.undoButton.setOnClickListener(null);
    binding.redoButton.setOnClickListener(null);
    binding.clearButton.setOnClickListener(null);

    smartGuideView.setEditor(null);

    Editor editor = editorData.getEditor();
    if (editor != null)
    {
      editor.getRenderer().close();
      editor.close();
    }
    editorView.setOnTouchListener(null);
    editorView.setEditor(null);

    if (contentPart != null)
    {
      contentPart.close();
      contentPart = null;
    }
    if (contentPackage != null)
    {
      contentPackage.close();
      contentPackage = null;
    }

    // IInkApplication has the ownership, do not close here
    engine = null;

    super.onDestroy();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    getMenuInflater().inflate(R.menu.main_activity_menu, menu);

    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    Editor editor = editorData.getEditor();
    if (item.getItemId() == R.id.menu_convert && editor != null && !editor.isClosed())
    {
      ConversionState[] supportedStates = editor.getSupportedTargetConversionStates(null);
      if (supportedStates.length > 0)
        editor.convert(null, supportedStates[0]);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void setMargins(Editor editor, @DimenRes int horizontalMarginRes, @DimenRes int verticalMarginRes)
  {
    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
    Configuration conf = editor.getConfiguration();
    float verticalMarginPX = getResources().getDimension(verticalMarginRes);
    float verticalMarginMM = 25.4f * verticalMarginPX / displayMetrics.ydpi;
    float horizontalMarginPX = getResources().getDimension(horizontalMarginRes);
    float horizontalMarginMM = 25.4f * horizontalMarginPX / displayMetrics.xdpi;
    conf.setNumber("text.margin.top", verticalMarginMM);
    conf.setNumber("text.margin.left", horizontalMarginMM);
    conf.setNumber("text.margin.right", horizontalMarginMM);
    conf.setNumber("math.margin.top", verticalMarginMM);
    conf.setNumber("math.margin.bottom", verticalMarginMM);
    conf.setNumber("math.margin.left", horizontalMarginMM);
    conf.setNumber("math.margin.right", horizontalMarginMM);
  }

  private void setInputMode(int inputMode)
  {
    editorData.getInputController().setInputMode(inputMode);
    binding.inputModeForcePenButton.setEnabled(inputMode != InputController.INPUT_MODE_FORCE_PEN);
    binding.inputModeForceTouchButton.setEnabled(inputMode != InputController.INPUT_MODE_FORCE_TOUCH);
    binding.inputModeAutoButton.setEnabled(inputMode != InputController.INPUT_MODE_AUTO);
  }

  private void invalidateIconButtons()
  {
    Editor editor = editorData.getEditor();
    if (editor == null)
      return;
    final boolean canUndo = editor.canUndo();
    final boolean canRedo = editor.canRedo();
    runOnUiThread(() -> {
      binding.undoButton.setEnabled(canUndo);
      binding.redoButton.setEnabled(canRedo);
      binding.clearButton.setEnabled(contentPart != null);
    });
  }
}
