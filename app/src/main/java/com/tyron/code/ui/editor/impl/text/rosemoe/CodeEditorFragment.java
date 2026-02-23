package com.tyron.code.ui.editor.impl.text.rosemoe;

import static io.github.rosemoe.sora2.text.EditorUtil.getDefaultColorScheme;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.ForwardingListener;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.itsaky.androidide.lsp.kotlin.KotlinLanguageServer;
import com.sun.tools.javac.util.JCDiagnostic;
import com.tyron.actions.ActionManager;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.DataContext;
import com.tyron.actions.util.DataContextUtils;
import com.tyron.builder.log.LogViewModel;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.FileManager;
import com.tyron.builder.project.api.Module;
import com.tyron.builder.project.listener.FileListener;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.analyzer.BaseTextmateAnalyzer;
import com.tyron.code.language.LanguageManager;
import com.tyron.code.language.java.JavaLanguage;
import com.tyron.code.language.kotlin.KotlinLanguage;
import com.tyron.code.language.lsp.SimpleLanguageClientImpl;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import com.tyron.code.language.xml.LanguageXML;
import com.tyron.code.ui.editor.CodeAssistCompletionLayout;
import com.tyron.code.ui.editor.CodeAssistCompletionWindow;
import com.tyron.code.ui.editor.EditorViewModel;
import com.tyron.code.ui.editor.Savable;
import com.tyron.code.ui.editor.impl.FileEditorManagerImpl;
import com.tyron.code.ui.editor.scheme.CompiledEditorScheme;
import com.tyron.code.ui.editor.shortcuts.ShortcutAction;
import com.tyron.code.ui.editor.shortcuts.ShortcutItem;
import com.tyron.code.ui.main.MainViewModel;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.code.ui.settings.EditorSettingsFragment;
import com.tyron.code.ui.theme.ThemeRepository;
import com.tyron.code.util.CoordinatePopupMenu;
import com.tyron.code.util.PopupMenuHelper;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.common.logging.IdeLog;
import com.tyron.common.util.AndroidUtilities;
import com.tyron.common.util.DebouncerStore;
import com.tyron.completion.java.util.DiagnosticUtil;
import com.tyron.completion.java.util.JavaDataContextUtil;
import com.tyron.completion.lsp.api.ILanguageServer;
import com.tyron.completion.lsp.api.ILanguageServerRegistry;
import com.tyron.completion.lsp.api.LspLanguage;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.diagnostics.DiagnosticProvider;
import com.tyron.editor.CharPosition;
import com.tyron.language.api.CodeAssistLanguage;
import com.tyron.resources.R;
import io.github.rosemoe.sora.event.ClickEvent;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.EditorKeyEvent;
import io.github.rosemoe.sora.event.Event;
import io.github.rosemoe.sora.event.InterceptTarget;
import io.github.rosemoe.sora.event.LongPressEvent;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.DirectAccessProps;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora2.text.EditorUtil;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.jetbrains.kotlin.com.intellij.util.ReflectionUtil;

// import com.tyron.editor.Content;

@SuppressWarnings("FieldCanBeLocal")
public class CodeEditorFragment extends Fragment
    implements Savable,
        SharedPreferences.OnSharedPreferenceChangeListener,
        FileListener,
        ProjectManager.OnProjectOpenListener {

  private static final Logger LOG = IdeLog.getCurrentLogger(CodeEditorFragment.class);

  public static final String KEY_LINE = "line";
  public static final String KEY_COLUMN = "column";
  public static final String KEY_PATH = "path";

  private static final Map<String, String> SERVER_MAP =
      Map.of(
          //    "java", JavaLanguageServer.SERVER_ID,
          //     "xml", XMLLanguageServer.SERVER_ID,
          "kt", KotlinLanguageServer.SERVER_ID);

  private ILanguageServer createLanguageServer(File file) {
    if (!file.isFile()) return null;

    String serverID = SERVER_MAP.get(getExtension(file));
    if (serverID == null) return null;

    return ILanguageServerRegistry.getDefault().getServer(serverID);
  }

  private static String getExtension(File file) {
    String name = file.getName();
    int lastDot = name.lastIndexOf('.');
    return lastDot == -1 ? "" : name.substring(lastDot + 1);
  }

  public static CodeEditorFragment newInstance(File file) {
    CodeEditorFragment fragment = new CodeEditorFragment();
    Bundle args = new Bundle();
    args.putString(KEY_PATH, file.getAbsolutePath());
    fragment.setArguments(args);
    return fragment;
  }

  /**
   * Creates a new instance of the editor with the the cursor positioned at the given line and
   * column
   *
   * @param file The file to be r ead
   * @param line The 0-based line
   * @param column The 0-based column
   * @return The editor instance
   */
  public static CodeEditorFragment newInstance(File file, int line, int column) {
    CodeEditorFragment fragment = new CodeEditorFragment();
    Bundle args = new Bundle();
    args.putInt(KEY_LINE, line);
    args.putInt(KEY_COLUMN, column);
    args.putString(KEY_PATH, file.getAbsolutePath());
    fragment.setArguments(args);
    return fragment;
  }

  /** Keys for saved states */
  private static final String EDITOR_LEFT_LINE_KEY = "line";

  private static final String EDITOR_LEFT_COLUMN_KEY = "column";
  private static final String EDITOR_RIGHT_LINE_KEY = "rightLine";
  private static final String EDITOR_RIGHT_COLUMN_KEY = "rightColumn";

  private CodeEditorView mEditor;

  private Language mLanguage;
  private File mCurrentFile = new File("");
  private MainViewModel mMainViewModel;

  private Bundle mSavedInstanceState;

  private boolean mCanSave = false;
  private boolean mReading = false;

  private View.OnTouchListener mDragToOpenListener;

  public CodeEditorFragment() {
    super(R.layout.code_editor_fragment);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mCurrentFile = new File(requireArguments().getString(KEY_PATH, ""));
    mMainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    mSavedInstanceState = savedInstanceState;
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);

    Cursor cursor = mEditor.getCursor();
    outState.putInt(EDITOR_LEFT_LINE_KEY, cursor.getLeftLine());
    outState.putInt(EDITOR_LEFT_COLUMN_KEY, cursor.getLeftColumn());
    outState.putInt(EDITOR_RIGHT_LINE_KEY, cursor.getRightLine());
    outState.putInt(EDITOR_RIGHT_COLUMN_KEY, cursor.getRightColumn());
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  private void onContentChange(com.tyron.editor.Content content) {
    if (true) return;

    if (com.tyron.completion.java.provider.CompletionEngine.isIndexing()) {
      return;
    }
    Language language = mEditor.getEditorLanguage();

    Module module = ProjectManager.getInstance().getCurrentProject().getModule(mCurrentFile);

    if (module == null) return;

    if (language instanceof CodeAssistLanguage)
      ((CodeAssistLanguage) language).onContentChange(mCurrentFile, content);

    if (language instanceof KotlinLanguage) return;
    if (language instanceof LanguageXML) return;

    ProgressManager.getInstance()
        .runLater(
            () -> {
              ServiceLoader<DiagnosticProvider> providers =
                  ServiceLoader.load(DiagnosticProvider.class);
              for (DiagnosticProvider provider : providers) {

                List<JCDiagnostic> diagnostics =
                    new ArrayList<>(provider.getDiagnostics(module, mCurrentFile));
                mEditor.setDiagnostics(
                    diagnostics.stream().map(DiagnosticWrapper::new).collect(Collectors.toList()));
              }
            },
            300);
  }

  public void hideEditorWindows() {
    mEditor.hideAutoCompleteWindow();
    mEditor.ensureWindowsDismissed();
  }

  /**
   * Return the {@link EditorColorScheme} from the specified path. If the path is null or does not
   * exist, the default color scheme is returned depending on the state of the device's theme
   *
   * @param path The file path to color scheme json file
   * @return The color scheme instance
   */
  @NonNull
  private ListenableFuture<TextMateColorScheme> getScheme(@Nullable String path) {
    if (path != null && new File(path).exists()) {
      return EditorSettingsFragment.getColorScheme(new File(path));
    } else {
      return Futures.immediateFailedFuture(new Throwable());
    }
  }

  @SuppressLint("RestrictedApi")
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    mCanSave = false;

    mEditor = view.findViewById(R.id.code_editor);
    mEditor.setEditable(false);
    configureEditor(mEditor);

    View topView = view.findViewById(R.id.top_view);
    EditorViewModel viewModel =
        new ViewModelProvider((ViewModelStoreOwner) this).get(EditorViewModel.class);
    viewModel
        .getAnalyzeState()
        .observe(
            getViewLifecycleOwner(),
            analyzing -> {
              if (analyzing) {
                topView.setVisibility(View.VISIBLE);
              } else {
                topView.setVisibility(View.GONE);
              }
            });
    mEditor.setViewModel(viewModel);
    ApplicationLoader.getDefaultPreferences().registerOnSharedPreferenceChangeListener(this);

    postConfigureEditor();

    String schemeValue =
        ApplicationLoader.getDefaultPreferences().getString(SharedPreferenceKeys.SCHEME, null);
    if (schemeValue != null
        && new File(schemeValue).exists()
        && ThemeRepository.getColorScheme(schemeValue) != null) {
      TextMateColorScheme scheme = ThemeRepository.getColorScheme(schemeValue);
      if (scheme != null) {
        mEditor.setColorScheme(scheme);
        mEditor.openFile(mCurrentFile);
        initializeLanguage();
        readOrWait();
      }
    } else {
      ListenableFuture<TextMateColorScheme> scheme = getScheme(schemeValue);
      Futures.addCallback(
          scheme,
          new FutureCallback<TextMateColorScheme>() {
            @Override
            public void onSuccess(@Nullable TextMateColorScheme result) {
              if (getContext() == null) {
                return;
              }
              assert result != null;
              ThemeRepository.putColorScheme(schemeValue, result);
              mEditor.setColorScheme(result);
              mEditor.openFile(mCurrentFile);
              initializeLanguage();
              readOrWait();
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
              if (getContext() == null) {
                return;
              }
              String key =
                  EditorUtil.isDarkMode(requireContext())
                      ? ThemeRepository.DEFAULT_NIGHT
                      : ThemeRepository.DEFAULT_LIGHT;
              TextMateColorScheme scheme = ThemeRepository.getColorScheme(key);
              if (scheme == null) {
                scheme = getDefaultColorScheme(requireContext());
                ThemeRepository.putColorScheme(key, scheme);
              }
              mEditor.setColorScheme(scheme);
              mEditor.openFile(mCurrentFile);
              initializeLanguage();
              readOrWait();
            }
          },
          ContextCompat.getMainExecutor(requireContext()));
    }
  }

  private void initializeLanguage() {
    mLanguage = LanguageManager.getInstance().get(mEditor, mCurrentFile);
    if (mLanguage == null) {
      mLanguage = new EmptyTextMateLanguage();
    }
    mEditor.setEditorLanguage(mLanguage);
  }

  private void configureEditor(@NonNull CodeEditorView editor) {
    // do not allow the user to edit, since at the time this is called
    // the contents may still be loading.
    editor.setEditable(false);
    editor.setColorScheme(new CompiledEditorScheme(requireContext()));
    editor.setBackgroundAnalysisEnabled(false);
    editor.setTypefaceText(
        ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono_regular));
    editor.getComponent(EditorAutoCompletion.class).setLayout(new CodeAssistCompletionLayout());
    editor.setLigatureEnabled(true);
    editor.setDiagnostics(new DiagnosticsContainer());
    editor.setHighlightCurrentBlock(false);
    editor.setHighlightBracketPair(false);
    editor.setEdgeEffectColor(Color.TRANSPARENT);
    editor.setUndoEnabled(true);
    editor.openFile(mCurrentFile);
    editor.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    editor.setInputType(
        EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

    SharedPreferences pref = ApplicationLoader.getDefaultPreferences();
    editor.setWordwrap(pref.getBoolean(SharedPreferenceKeys.EDITOR_WORDWRAP, false));
    editor.setTextSize(Integer.parseInt(pref.getString(SharedPreferenceKeys.FONT_SIZE, "12")));

    DirectAccessProps props = editor.getProps();
    props.overScrollEnabled = false;
    props.allowFullscreen = false;
    props.stickyScroll = true;
    props.deleteEmptyLineFast = pref.getBoolean(SharedPreferenceKeys.DELETE_WHITESPACES, false);
  }

  private void postConfigureEditor() {
    // noinspection ClickableViewAccessibility
    mEditor.setOnTouchListener(
        (view12, motionEvent) -> {
          if (mDragToOpenListener instanceof ForwardingListener) {
            PopupMenuHelper.setForwarding((ForwardingListener) mDragToOpenListener);
            // noinspection RestrictedApi
            mDragToOpenListener.onTouch(view12, motionEvent);
          }
          return false;
        });

    mEditor.subscribeEvent(
        EditorKeyEvent.class,
        (event, unsubscribe) -> {
          CodeAssistCompletionWindow window =
              (CodeAssistCompletionWindow) mEditor.getComponent(EditorAutoCompletion.class);
          if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
              || event.getKeyCode() == KeyEvent.KEYCODE_TAB) {
            if (window.isShowing() && window.trySelect()) {
              event.setResult(true);

              // KeyEvent cannot be intercepted???
              // workaround
              Field mInterceptTargets =
                  ReflectionUtil.findFieldInHierarchy(
                      Event.class, field -> "mInterceptTargets".equals(field.getName()));
              if (mInterceptTargets == null) return;
              mInterceptTargets.setAccessible(true);
              try {
                mInterceptTargets.set(event, InterceptTarget.TARGET_EDITOR);
              } catch (IllegalAccessException e) {
                throw new RuntimeException("REFLECTION FAILED");
              }

              mEditor.requestFocus();
            }
          }
        });

    mEditor.subscribeEvent(
        LongPressEvent.class,
        (event, unsubscribe) -> {
          event.intercept();

          updateFile(mEditor.getText());
          Cursor cursor = mEditor.getCursor();

          CharSequence text = mEditor.getText();
          int textLength = text.length();

          if (cursor.isSelected()) {
            int index = mEditor.getCharIndex(event.getLine(), event.getColumn());

            if (index >= 0 && index < textLength) {
              int cursorLeft = cursor.getLeft();
              int cursorRight = cursor.getRight();
              char c = text.charAt(index);

              if (Character.isWhitespace(c)) {
                mEditor.setSelection(event.getLine(), event.getColumn());
              } else if (index < cursorLeft || index > cursorRight) {
                EditorUtil.selectWord(mEditor, event.getLine(), event.getColumn());
              }
            } else {
              mEditor.setSelection(event.getLine(), event.getColumn());
            }
          } else {
            int index = event.getIndex();

            if (index >= 0 && index < textLength) {
              char c = text.charAt(index);
              if (!Character.isWhitespace(c)) {
                EditorUtil.selectWord(mEditor, event.getLine(), event.getColumn());
              } else {
                mEditor.setSelection(event.getLine(), event.getColumn());
              }
            } else {
              mEditor.setSelection(event.getLine(), event.getColumn());
            }
          }

          ProgressManager.getInstance()
              .runLater(
                  () -> {
                    showPopupMenu(event);
                  });
        });

    mEditor.subscribeEvent(
        ClickEvent.class,
        (event, unsubscribe) -> {
          try {
            Cursor cursor = mEditor.getCursor();
            if (cursor != null && cursor.isSelected()) {
              int index = mEditor.getCharIndex(event.getLine(), event.getColumn());
              String text = mEditor.getText().toString();

              if (index >= 0 && index < text.length()) {
                int cursorLeft = cursor.getLeft();
                int cursorRight = cursor.getRight();

                if (!EditorUtil.isWhitespace(text.charAt(index) + "")
                    && index >= cursorLeft
                    && index <= cursorRight) {
                  mEditor.showSoftInput();
                  event.intercept();
                }
              }
            }
          } catch (Exception e) {
            LOG.severe("Error ClickEvent: " + e.getMessage());
          }
        });

    mEditor.subscribeEvent(
        ContentChangeEvent.class,
        (event, unsubscribe) -> {
          if (event.getAction() == ContentChangeEvent.ACTION_SET_NEW_TEXT) {
            return;
          }
          updateFile(event.getEditor().getText());
          ProgressManager.getInstance()
              .runNonCancelableAsync(
                  () ->
                      DebouncerStore.DEFAULT
                          .registerOrGetDebouncer("contentChange")
                          .debounce(
                              300,
                              () -> {
                                try {
                                  onContentChange(mEditor.getContent());
                                } catch (Throwable t) {
                                  LOG.severe("Error in onContentChange" + ": " + t);
                                }
                              }));
        });

    LogViewModel logViewModel = new ViewModelProvider(requireActivity()).get(LogViewModel.class);
    mEditor.setDiagnosticsListener(
        diagnostics -> {
          for (DiagnosticWrapper diagnostic : diagnostics) {
            DiagnosticUtil.setLineAndColumn(diagnostic, mEditor);
          }
          ProgressManager.getInstance()
              .runLater(() -> logViewModel.updateLogs(LogViewModel.DEBUG, diagnostics));
        });
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
    if (mEditor == null) {
      return;
    }
    switch (key) {
      case SharedPreferenceKeys.FONT_SIZE:
        mEditor.setTextSize(Integer.parseInt(pref.getString(key, "14")));
        break;
      case SharedPreferenceKeys.EDITOR_WORDWRAP:
        mEditor.setWordwrap(pref.getBoolean(key, false));
        break;
      case SharedPreferenceKeys.DELETE_WHITESPACES:
        mEditor.getProps().deleteEmptyLineFast =
            pref.getBoolean(SharedPreferenceKeys.DELETE_WHITESPACES, false);
        break;
      case SharedPreferenceKeys.SCHEME:
        ListenableFuture<TextMateColorScheme> scheme =
            getScheme(pref.getString(SharedPreferenceKeys.SCHEME, null));
        Futures.addCallback(
            scheme,
            new FutureCallback<TextMateColorScheme>() {
              @Override
              public void onSuccess(@Nullable TextMateColorScheme result) {
                if (getContext() == null) {
                  return;
                }
                assert result != null;
                mEditor.setColorScheme(result);
                if (mLanguage.getAnalyzeManager() instanceof BaseTextmateAnalyzer) {
                  mLanguage.getAnalyzeManager().rerun();
                }
              }

              @Override
              public void onFailure(@NonNull Throwable t) {
                if (getContext() == null) {
                  return;
                }
                mEditor.setColorScheme(getDefaultColorScheme(requireContext()));
                mLanguage.getAnalyzeManager().rerun();
              }
            },
            ContextCompat.getMainExecutor(requireContext()));
        break;
    }
  }

  /** Show the popup menu with the actions api */
  private void showPopupMenu(LongPressEvent event) {
    MotionEvent e = event.getCausingEvent();
    if (e == null || requireContext() == null || mEditor == null) {
      // Handle null values here
      return;
    }

    CoordinatePopupMenu popupMenu =
        new CoordinatePopupMenu(requireContext(), mEditor, Gravity.BOTTOM);
    if (popupMenu == null) {
      // Handle null popupMenu here
      return;
    }

    DataContext dataContext = createDataContext();
    if (dataContext == null) {
      // Handle null dataContext here
      return;
    }

    ActionManager actionManager = ActionManager.getInstance();
    if (actionManager == null) {
      // Handle null actionManager here
      return;
    }

    Menu menu = popupMenu.getMenu();
    if (menu == null) {
      // Handle null menu here
      return;
    }

    actionManager.fillMenu(dataContext, menu, ActionPlaces.EDITOR, true, false);
    popupMenu.show((int) e.getX(), ((int) e.getY()) - AndroidUtilities.dp(24));

    // we don't want to enable the drag to open listener right away,
    // this may cause the buttons to be clicked right away
    // so wait for a few ms
    ProgressManager progressManager = ProgressManager.getInstance();
    if (progressManager != null) {
      progressManager.runLater(
          () -> {
            popupMenu.setOnDismissListener(d -> mDragToOpenListener = null);
            mDragToOpenListener = popupMenu.getDragToOpenListener();
          },
          300);
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();

    Project currentProject = ProjectManager.getInstance().getCurrentProject();
    if (currentProject != null) {
      Module module = currentProject.getModule(mCurrentFile);
      if (module != null) {
        module.getFileManager().removeSnapshotListener(this);
      }
    }
    ProjectManager.getInstance().removeOnProjectOpenListener(this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (ProjectManager.getInstance().getCurrentProject() != null && mCanSave) {
      ProgressManager.getInstance()
          .runNonCancelableAsync(
              () ->
                  ProjectManager.getInstance()
                      .getCurrentProject()
                      .getModule(mCurrentFile)
                      .getFileManager()
                      .closeFileForSnapshot(mCurrentFile));
      mEditor.dispatchDocumentSaveEvent();
    }
    ApplicationLoader.getDefaultPreferences().unregisterOnSharedPreferenceChangeListener(this);
    mEditor.dispatchDocumentCloseEvent();
  }

  @Override
  public void onPause() {
    super.onPause();

    hideEditorWindows();

    save(true);
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();

    //    mEditor.setBackgroundAnalysisEnabled(false);
  }

  @Override
  public void onSnapshotChanged(File file, CharSequence contents) {
    if (mCurrentFile.equals(file)) {
      if (mEditor != null) {
        if (!mEditor.getText().toString().contentEquals(contents)) {
          Cursor cursor = mEditor.getCursor();
          int left = cursor.getLeft();
          mEditor.setText(contents);

          if (left > contents.length()) {
            left = contents.length();
          }
          CharPosition position = mEditor.getCharPosition(left);
          mEditor.setSelection(position.getLine(), position.getColumn());
        }
      }
    }
  }

  @Override
  public boolean canSave() {
    return mCanSave && !mReading;
  }

  @Override
  public void save(boolean toDisk) {
    if (!mCanSave || mReading) {
      return;
    }

    // don't save if the file has been deleted externally but its still opened in the editor,
    if (!mCurrentFile.exists()) {
      return;
    }

    if (ProjectManager.getInstance().getCurrentProject() != null && !toDisk) {
      ProjectManager.getInstance()
          .getCurrentProject()
          .getModule(mCurrentFile)
          .getFileManager()
          .setSnapshotContent(mCurrentFile, mEditor.getText().toString(), false);
    } else {
      ProgressManager.getInstance()
          .runNonCancelableAsync(
              () -> {
                try {
                  FileUtils.writeStringToFile(
                      mCurrentFile, mEditor.getText().toString(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                  LOG.severe(
                      "Unable to save file: "
                          + mCurrentFile.getAbsolutePath()
                          + "\n"
                          + "Reason: "
                          + e.getMessage());
                }
              });
    }
    mEditor.dispatchDocumentSaveEvent();
  }

  @Override
  public void onProjectOpen(Project project) {
    ProgressManager.getInstance().runLater(() -> readFile(project, mSavedInstanceState));
    if (mEditor.getEditorLanguage() instanceof KotlinLanguage
        && ((KotlinLanguage) mEditor.getEditorLanguage()).kotlinEnvironment == null)
      ((KotlinLanguage) mEditor.getEditorLanguage()).initEnv();
    if (mEditor.getEditorLanguage() instanceof LspLanguage) {
      ILanguageServer languageServer = createLanguageServer(mCurrentFile);
      mEditor.setLanguageServer(languageServer);
      ((LspLanguage) mEditor.getEditorLanguage()).setLanguageServer(languageServer);
      if (SimpleLanguageClientImpl.isInitialized()) {
        mEditor.setLanguageClient(SimpleLanguageClientImpl.getInstance());
      }
      mEditor.dispatchDocumentOpenEvent();
    }
  }

  /**
   * Read the file immediately if there is a project open. If not, wait for the project to be opened
   * first.
   */
  private void readOrWait() {
    if (ProjectManager.getInstance().getCurrentProject() != null) {
      readFile(ProjectManager.getInstance().getCurrentProject(), mSavedInstanceState);
      if (mEditor.getEditorLanguage() instanceof KotlinLanguage
          && ((KotlinLanguage) mEditor.getEditorLanguage()).kotlinEnvironment == null)
        ((KotlinLanguage) mEditor.getEditorLanguage()).initEnv();
      if (mEditor.getEditorLanguage() instanceof LspLanguage) {
        ILanguageServer languageServer = createLanguageServer(mCurrentFile);
        mEditor.setLanguageServer(languageServer);
        ((LspLanguage) mEditor.getEditorLanguage()).setLanguageServer(languageServer);
        if (SimpleLanguageClientImpl.isInitialized()) {
          mEditor.setLanguageClient(SimpleLanguageClientImpl.getInstance());
        }
        mEditor.dispatchDocumentOpenEvent();
      }
    } else {
      ProjectManager.getInstance().addOnProjectOpenListener(this);
    }
  }

  private ListenableFuture<String> readFile() {
    return Futures.submitAsync(
        () -> {
          FileSystemManager manager = VFS.getManager();
          FileObject fileObject = manager.resolveFile(mCurrentFile.toURI());
          FileContent content = fileObject.getContent();
          return Futures.immediateFuture(content.getString(StandardCharsets.UTF_8));
        },
        Executors.newSingleThreadExecutor());
  }

  private void readFile(@NonNull Project currentProject, @Nullable Bundle savedInstanceState) {
    mCanSave = false;
    Module module = currentProject.getModule(mCurrentFile);
    FileManager fileManager = module.getFileManager();
    fileManager.addSnapshotListener(this);

    // the file is already opened, so no need to load it.
    if (fileManager.isOpened(mCurrentFile)) {
      Optional<CharSequence> contents = fileManager.getFileContent(mCurrentFile);
      if (contents.isPresent()) {
        mEditor.setText(contents.get());
        return;
      }
    }

    mReading = true;
    mEditor.setBackgroundAnalysisEnabled(false);
    ListenableFuture<String> future = readFile();
    Futures.addCallback(
        future,
        new FutureCallback<String>() {
          @Override
          public void onSuccess(@Nullable String result) {
            mReading = false;
            if (getContext() == null) {
              mCanSave = false;
              return;
            }
            if (mLanguage == null) {
              return;
            }
            mCanSave = true;
            mEditor.setBackgroundAnalysisEnabled(true);
            mEditor.setEditable(true);
            fileManager.openFileForSnapshot(mCurrentFile, result);

            Bundle bundle = new Bundle();
            bundle.putBoolean("loaded", true);
            bundle.putBoolean("bg", true);
            mEditor.setText(result, bundle);

            if (savedInstanceState != null) {
              restoreState(savedInstanceState);
            } else {
              int line = requireArguments().getInt(KEY_LINE, 0);
              int column = requireArguments().getInt(KEY_COLUMN, 0);
              Content text = mEditor.getText();
              if (line < text.getLineCount() && column < text.getColumnCount(line)) {
                setCursorPosition(line, column);
              }
            }
            checkCanSave();
          }

          @Override
          public void onFailure(@NonNull Throwable t) {
            mCanSave = false;
            mReading = false;
            if (getContext() != null) {
              checkCanSave();
            }

            LOG.severe(
                "Unable to read current file: "
                    + mCurrentFile
                    + "\n"
                    + "Reason: "
                    + t.getMessage());
          }
        },
        ContextCompat.getMainExecutor(requireContext()));
  }

  private void checkCanSave() {
    if (!mCanSave) {
      Snackbar snackbar =
          Snackbar.make(mEditor, R.string.editor_error_file, Snackbar.LENGTH_INDEFINITE)
              .setAction(
                  R.string.menu_close,
                  v -> FileEditorManagerImpl.getInstance().closeFile(mCurrentFile));
      ViewGroup snackbarView = (ViewGroup) snackbar.getView();
      AndroidUtilities.setMargins(snackbarView, 0, 0, 0, 50);
      snackbar.show();
    }
  }

  private void restoreState(@NonNull Bundle savedInstanceState) {
    int leftLine = savedInstanceState.getInt(EDITOR_LEFT_LINE_KEY, 0);
    int leftColumn = savedInstanceState.getInt(EDITOR_LEFT_COLUMN_KEY, 0);
    int rightLine = savedInstanceState.getInt(EDITOR_RIGHT_LINE_KEY, 0);
    int rightColumn = savedInstanceState.getInt(EDITOR_RIGHT_COLUMN_KEY, 0);

    Content text = mEditor.getText();
    if (leftLine > text.getLineCount() || rightLine > text.getLineCount()) {
      return;
    }
    if (leftLine != rightLine && leftColumn != rightColumn) {
      mEditor.setSelectionRegion(leftLine, leftColumn, rightLine, rightColumn, true);
    } else {
      mEditor.setSelection(leftLine, leftColumn);
    }
  }

  private void updateFile(CharSequence contents) {
    Project project = ProjectManager.getInstance().getCurrentProject();
    if (project == null) {
      return;
    }
    Module module = project.getModule(mCurrentFile);
    if (module != null) {
      if (!module.getFileManager().isOpened(mCurrentFile)) {
        return;
      }
      module.getFileManager().setSnapshotContent(mCurrentFile, contents.toString(), this);
    }
  }

  public CodeEditorView getEditor() {
    return mEditor;
  }

  /** Undo the text in the editor if possible, if not the call is ignored */
  public void undo() {
    if (mEditor == null) {
      return;
    }
    if (mEditor.canUndo()) {
      mEditor.undo();
    }
  }

  /** Redo the text in the editor if possible, if not the call is ignored */
  public void redo() {
    if (mEditor == null) {
      return;
    }
    if (mEditor.canRedo()) {
      mEditor.redo();
    }
  }

  /**
   * Sets the position of the cursor in the editor
   *
   * @param line zero-based line.
   * @param column zero-based column.
   */
  public void setCursorPosition(int line, int column) {
    if (mEditor != null) {
      mEditor.getCursor().set(line, column);
    }
  }

  /**
   * Perform a shortcut item to the editor
   *
   * @param item the item to be performed
   */
  public void performShortcut(ShortcutItem item) {
    if (mEditor == null) {
      return;
    }
    for (ShortcutAction action : item.actions) {
      action.apply(mEditor, item);
    }
  }

  public void format() {
    if (mEditor != null) {
      mEditor.formatCodeAsync();
    }
  }

  public void search() {
    if (mEditor != null) {

      mEditor.getSearcher().stopSearch();
      mEditor.beginSearchMode();
    }
  }

  /** Notifies the editor to analyze and highlight the current text */
  public void analyze() {
    if (mEditor != null && !mReading) {
      mEditor.rerunAnalysis();
    }
  }

  /**
   * Create the data context specific to this fragment for use with the actions API.
   *
   * @return the data context.
   */
  private DataContext createDataContext() {
    Project currentProject = ProjectManager.getInstance().getCurrentProject();

    DataContext dataContext = DataContextUtils.getDataContext(mEditor);
    dataContext.putData(CommonDataKeys.PROJECT, currentProject);
    dataContext.putData(CommonDataKeys.ACTIVITY, requireActivity());
    dataContext.putData(CommonDataKeys.FILE_EDITOR_KEY, mMainViewModel.getCurrentFileEditor());
    dataContext.putData(CommonDataKeys.FILE, mCurrentFile);
    dataContext.putData(CommonDataKeys.EDITOR, mEditor);

    if (currentProject != null && mLanguage instanceof JavaLanguage) {
      JavaDataContextUtil.addEditorKeys(
          dataContext, currentProject, mCurrentFile, mEditor.getCursor().getLeft());
      return dataContext;
    }

    DiagnosticWrapper diagnosticWrapper =
        DiagnosticUtil.getDiagnosticWrapper(
            mEditor.getDiagnosticsList(),
            mEditor.getCursor().getLeft(),
            mEditor.getCursor().getRight());
    if (diagnosticWrapper == null && mLanguage instanceof LanguageXML) {
      diagnosticWrapper =
          DiagnosticUtil.getXmlDiagnosticWrapper(
              mEditor.getDiagnosticsList(), mEditor.getCursor().getLeftLine());
    }
    dataContext.putData(CommonDataKeys.DIAGNOSTIC, diagnosticWrapper);
    return dataContext;
  }
}
