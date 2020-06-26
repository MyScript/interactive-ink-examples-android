// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.myscript.iink.Configuration;
import com.myscript.iink.ContentBlock;
import com.myscript.iink.ContentPart;
import com.myscript.iink.ConversionState;
import com.myscript.iink.Editor;
import com.myscript.iink.Engine;
import com.myscript.iink.IEditorListener;
import com.myscript.iink.MimeType;

import com.myscript.iink.uireferenceimplementation.FontUtils;
import com.myscript.iink.uireferenceimplementation.ImageLoader;
import com.myscript.iink.uireferenceimplementation.EditorView;
import com.myscript.iink.uireferenceimplementation.IInputControllerListener;
import com.myscript.iink.uireferenceimplementation.InputController;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener
{
  private static final String TAG = "MainActivity";

  private static final String INPUT_MODE_KEY = "inputMode";

  protected Engine engine;

  protected EditorView editorView;

  protected DocumentController documentController;

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
    String tempDir = getExternalFilesDir(null).getPath() + File.separator + "tmp";
    conf.setString("content-package.temp-folder", tempDir);

    setContentView(R.layout.activity_main);

    editorView = findViewById(R.id.editor_view);

    // load fonts
    AssetManager assetManager = getApplicationContext().getAssets();
    Map<String, Typeface> typefaceMap = FontUtils.loadFontsFromAssets(assetManager);
    editorView.setTypefaces(typefaceMap);

    editorView.setEngine(engine);

    final Editor editor = editorView.getEditor();
    editor.addListener(new IEditorListener()
    {
      @Override
      public void partChanging(Editor editor, ContentPart oldPart, ContentPart newPart)
      {
        // no-op
      }

      @Override
      public void partChanged(Editor editor)
      {
        invalidateOptionsMenu();
        invalidateIconButtons();
      }

      @Override
      public void contentChanged(Editor editor, String[] blockIds)
      {
        invalidateOptionsMenu();
        invalidateIconButtons();
      }

      @Override
      public void onError(Editor editor, String blockId, String message)
      {
        Log.e(TAG, "Failed to edit block \"" + blockId + "\"" + message);
      }
    });

    editorView.setImageLoader(new ImageLoader(editor));
    editorView.setInputControllerListener(new IInputControllerListener()
    {
      @Override
      public boolean onLongPress(final float x, final float y, final ContentBlock contentBlock)
      {
        return displayContextMenu(x, y, contentBlock);
      }
    });

    int inputMode = InputController.INPUT_MODE_FORCE_PEN; // If using an active pen, put INPUT_MODE_AUTO here
    if (savedInstanceState != null)
      inputMode = savedInstanceState.getInt(INPUT_MODE_KEY, inputMode);
    setInputMode(inputMode);

    documentController = new DocumentController(this, editorView);
    final String fileName = documentController.getSavedFileName();
    final int partIndex = documentController.getSavedPartIndex();

    // wait for view size initialization before setting part
    editorView.post(new Runnable()
    {
      @Override
      public void run()
      {
        if (fileName != null)
          documentController.openPart(fileName, partIndex);
        else
          documentController.newPart();
      }
    });

    findViewById(R.id.button_input_mode_forcePen).setOnClickListener(this);
    findViewById(R.id.button_input_mode_forceTouch).setOnClickListener(this);
    findViewById(R.id.button_input_mode_auto).setOnClickListener(this);
    findViewById(R.id.button_undo).setOnClickListener(this);
    findViewById(R.id.button_redo).setOnClickListener(this);
    findViewById(R.id.button_clear).setOnClickListener(this);

    invalidateIconButtons();
  }

  @Override
  protected void onDestroy()
  {
    editorView.setOnTouchListener(null);
    editorView.close();

    documentController.close();

    // IInkApplication has the ownership, do not close here
    engine = null;

    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState)
  {
    documentController.saveToTemp();
    outState.putInt(INPUT_MODE_KEY, editorView.getInputMode());
    super.onSaveInstanceState(outState);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    getMenuInflater().inflate(R.menu.activity_main, menu);

    Editor editor = editorView.getEditor();
    boolean hasEditor = editor != null && !editor.isClosed();
    boolean hasPart = documentController.hasPart();
    int partIndex = documentController.getPartIndex();
    int partCount = documentController.getPartCount();

    MenuItem previousPartMenuItem = menu.findItem(R.id.menu_previousPart);
    previousPartMenuItem.setEnabled(hasPart && partIndex - 1 >= 0);
    MenuItem nextPartMenuItem = menu.findItem(R.id.menu_nextPart);
    nextPartMenuItem.setEnabled(hasPart && partIndex + 1 < partCount);
    MenuItem zoomInMenuItem = menu.findItem(R.id.menu_zoomIn);
    zoomInMenuItem.setEnabled(hasEditor && hasPart);
    MenuItem zoomOutMenuItem = menu.findItem(R.id.menu_zoomOut);
    zoomOutMenuItem.setEnabled(hasEditor && hasPart);
    MenuItem resetViewMenuItem = menu.findItem(R.id.menu_resetView);
    resetViewMenuItem.setEnabled(hasEditor && hasPart);
    MenuItem convertMenuItem = menu.findItem(R.id.menu_convert);
    convertMenuItem.setEnabled(hasPart);
    MenuItem exportMenuItem = menu.findItem(R.id.menu_export);
    exportMenuItem.setEnabled(hasEditor && hasPart);

    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    Editor editor = editorView.getEditor();
    switch (item.getItemId())
    {
      case R.id.menu_newPart:
        return documentController.newPart();
      case R.id.menu_previousPart:
        return documentController.previousPart();
      case R.id.menu_nextPart:
        return documentController.nextPart();
      case R.id.menu_zoomIn:
        return documentController.zoomIn();
      case R.id.menu_zoomOut:
        return documentController.zoomOut();
      case R.id.menu_resetView:
        return documentController.resetView();
      case R.id.menu_convert:
        return documentController.convert(null);
      case R.id.menu_export:
        return documentController.export(null);
      case R.id.menu_newPackage:
        return documentController.newPackage();
      case R.id.menu_openPackage:
        return documentController.openPackage();
      case R.id.menu_savePackage:
        return documentController.savePackage();
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onClick(View v)
  {
    switch (v.getId())
    {
      case R.id.button_input_mode_forcePen:
        setInputMode(InputController.INPUT_MODE_FORCE_PEN);
        break;
      case R.id.button_input_mode_forceTouch:
        setInputMode(InputController.INPUT_MODE_FORCE_TOUCH);
        break;
      case R.id.button_input_mode_auto:
        setInputMode(InputController.INPUT_MODE_AUTO);
        break;
      case R.id.button_undo:
        editorView.getEditor().undo();
        break;
      case R.id.button_redo:
        editorView.getEditor().redo();
        break;
      case R.id.button_clear:
        editorView.getEditor().clear();
        break;
      default:
        Log.e(TAG, "Failed to handle click event");
        break;
    }
  }

  private boolean addImage(final float x, final float y)
  {
    final File[] files = getExternalFilesDir(null).listFiles(new FilenameFilter()
    {
      @Override
      public boolean accept(File dir, String name)
      {
        return (name.endsWith(".gif") || name.endsWith(".png")
            || name.endsWith(".svg") || name.endsWith(".jpg")
            || name.endsWith(".jpeg") || name.endsWith(".jpe"));
      }
    });

    if (files.length == 0)
    {
      Log.e(TAG, "Failed to add image, image list is empty");
      return false;
    }

    String[] fileNames = new String[files.length];
    for (int i = 0; i < files.length; ++i)
      fileNames[i] = files[i].getName();

    final int[] selected = new int[]{0};
    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
    dialogBuilder.setTitle(R.string.addImage_title);

    dialogBuilder.setSingleChoiceItems(fileNames, selected[0], new DialogInterface.OnClickListener()
    {
      @Override
      public void onClick(DialogInterface dialog, int which)
      {
        selected[0] = which;
      }
    });

    dialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
    {
      @Override
      public void onClick(DialogInterface dialog, int which)
      {
        try
        {
          File file = files[selected[0]];

          if (file != null)
          {
            MimeType mimeType = null;

            for (MimeType mimeType_ : MimeType.values())
            {
              if (!mimeType_.isImage())
                continue;

              String fileExtensions = mimeType_.getFileExtensions();

              if (fileExtensions == null)
                continue;

              String[] extensions = fileExtensions.split(" *, *");

              for (int i = 0; i < extensions.length; ++i)
              {
                if (file.getName().endsWith(extensions[i]))
                {
                  mimeType = mimeType_;
                  break;
                }
              }
            }

            if (mimeType != null)
            {
              editorView.getEditor().addImage(x, y, file, mimeType);
            }
          }
        }
        catch (Exception e)
        {
          Toast.makeText(MainActivity.this, "Failed to add image", Toast.LENGTH_LONG).show();
        }
      }
    });

    dialogBuilder.setNegativeButton(R.string.cancel, null);

    AlertDialog dialog = dialogBuilder.create();
    dialog.show();

    return true;
  }

  private final boolean addBlock(final float x, final float y, final String blockType)
  {
    final Editor editor = editorView.getEditor();
    final MimeType[] mimeTypes = editor.getSupportedAddBlockDataMimeTypes(blockType);

    if (mimeTypes.length == 0)
    {
      editor.addBlock(x, y, blockType);
      return true;
    }

    final ArrayList<String> typeDescriptions = new ArrayList<String>();

    for (MimeType mimeType : mimeTypes)
      typeDescriptions.add(mimeType.getTypeName());

    if (typeDescriptions.isEmpty())
      return false;

    final int[] selected = new int[]{0};
    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

    dialogBuilder.setTitle(R.string.addType_title);
    dialogBuilder.setSingleChoiceItems(typeDescriptions.toArray(new String[0]), selected[0], new DialogInterface.OnClickListener()
    {
      @Override
      public void onClick(DialogInterface dialog, int which)
      {
        selected[0] = which;
      }
    });
    dialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
    {
      @Override
      public void onClick(DialogInterface dialog, int which)
      {
        addBlock_(x, y, blockType, mimeTypes[selected[0]]);
      }
    });
    dialogBuilder.setNegativeButton(R.string.cancel, null);

    AlertDialog dialog = dialogBuilder.create();
    dialog.show();

    return true;
  }

  private final boolean addBlock_(final float x, final float y, final String blockType, final MimeType mimeType)
  {
    final EditText input = new EditText(this);

    LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    input.setLayoutParams(params);
    input.setMinLines(5);
    input.setMaxLines(5);
    input.setGravity(Gravity.TOP|Gravity.LEFT);

    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
    dialogBuilder.setTitle(R.string.addData_title);
    dialogBuilder.setView(input);
    dialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
    {
      @Override
      public void onClick(DialogInterface dialog, int which)
      {
        try
        {
          String data = input.getText().toString();

          if (!data.isEmpty())
            editorView.getEditor().addBlock(x, y, blockType, mimeType, data);
        }
        catch (Exception e)
        {
          Toast.makeText(MainActivity.this, "Failed to add block", Toast.LENGTH_LONG).show();
        }
      }
    });

    dialogBuilder.setNegativeButton(R.string.cancel, null);

    AlertDialog dialog = dialogBuilder.create();
    dialog.show();

    return true;
  }

  private boolean displayContextMenu(final float x, final float y, final ContentBlock contentBlock_)
  {
    final Editor editor = editorView.getEditor();

    final ContentPart part = editor.getPart();
    if (part == null)
      return true;

    final ContentBlock rootBlock = editor.getRootBlock();
    final ContentBlock contentBlock = (contentBlock_ != null) && !contentBlock_.getType().equals("Container") ? contentBlock_ : rootBlock;

    final boolean isRoot = contentBlock.getId().equals(rootBlock.getId());

    final boolean onRawContent = part.getType().equals("Raw Content");
    final boolean onTextDocument = part.getType().equals("Text Document");

    final boolean isEmpty = editor.isEmpty(contentBlock);

    final String[] supportedTypes = editor.getSupportedAddBlockTypes();
    //final MimeType[] supportedExports = editor.getSupportedExportMimeTypes(onRawContent ? rootBlock : contentBlock);
    //final MimeType[] supportedImports = editor.getSupportedImportMimeTypes(contentBlock);
    final ConversionState[] supportedStates = editor.getSupportedTargetConversionStates(contentBlock);

    final boolean hasTypes = supportedTypes.length > 0;
    //final boolean hasExports = supportedExports.length > 0;
    //final boolean hasImports = supportedImports.length > 0;
    final boolean hasStates = supportedStates.length > 0;

    final boolean displayConvert = hasStates && !isEmpty;
    final boolean displayAddBlock = hasTypes && isRoot;
    final boolean displayAddImage = false; // hasTypes && isRoot;
    final boolean displayRemove = !isRoot;
    final boolean displayCopy = !onTextDocument || !isRoot;
    final boolean displayPaste = isRoot;
    final boolean displayImport = false; // hasImports;
    final boolean displayExport = false; // hasExports;

    final ArrayList<String> items = new ArrayList<>();

    if (displayAddBlock)
    {
      for (String blockType : supportedTypes)
        items.add("Add " + blockType);
    }

    if (displayAddImage)
      items.add("Add Image");

    if (displayRemove)
      items.add("Remove");

    if (displayConvert)
      items.add("Convert");

    if (displayCopy)
      items.add("Copy");

    if (displayPaste)
      items.add("Paste");

    if (displayImport)
      items.add("Import");

    if (displayExport)
      items.add("Export");

    if (items.isEmpty())
      return true;

    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
    dialogBuilder.setTitle(contentBlock.getType() + " (id: " + contentBlock.getId() + ")");
    dialogBuilder.setItems(items.toArray(new String[items.size()]), new DialogInterface.OnClickListener()
    {
      @Override
      public void onClick(DialogInterface dialog, int which)
      {
        try
        {
          String item = items.get(which);

          if (item.equals("Convert"))
          {
            editor.convert(contentBlock, supportedStates[0]);
          }
          else if (item.equals("Remove"))
          {
            editor.removeBlock(contentBlock);
          }
          else if (item.equals("Copy"))
          {
            editor.copy(contentBlock);
          }
          else if (item.equals("Paste"))
          {
            editor.paste(x, y);
          }
          else if (item.equals("Import"))
          {
            // TODO
          }
          else if (item.equals("Export"))
          {
            // TODO
          }
          else if (item.equals("Add Image"))
          {
            addImage(x, y);
          }
          else if (item.startsWith("Add "))
          {
            String blockType = item.substring(4);
            addBlock(x, y, blockType);
          }
        }
        catch (Exception e)
        {
          Toast.makeText(MainActivity.this, "Operation failed : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        dialog.dismiss();
      }
    });
    dialogBuilder.show();
    return true;
  }

  private void setInputMode(int inputMode)
  {
    editorView.setInputMode(inputMode);
    findViewById(R.id.button_input_mode_forcePen).setEnabled(inputMode != InputController.INPUT_MODE_FORCE_PEN);
    findViewById(R.id.button_input_mode_forceTouch).setEnabled(inputMode != InputController.INPUT_MODE_FORCE_TOUCH);
    findViewById(R.id.button_input_mode_auto).setEnabled(inputMode != InputController.INPUT_MODE_AUTO);
  }

  private void invalidateIconButtons()
  {
    Editor editor = editorView.getEditor();
    final boolean canUndo = editor.canUndo();
    final boolean canRedo = editor.canRedo();
    runOnUiThread(new Runnable()
    {
      @Override
      public void run()
      {
        ImageButton imageButtonUndo = (ImageButton) findViewById(R.id.button_undo);
        imageButtonUndo.setEnabled(canUndo);
        ImageButton imageButtonRedo = (ImageButton) findViewById(R.id.button_redo);
        imageButtonRedo.setEnabled(canRedo);
        ImageButton imageButtonClear = (ImageButton) findViewById(R.id.button_clear);
        imageButtonClear.setEnabled(documentController != null && documentController.hasPart());
      }
    });
  }
}
