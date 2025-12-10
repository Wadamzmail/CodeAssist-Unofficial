package com.tyron.code.analyzer;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.code.language.DiagnosticSpanMapUpdater;
import com.tyron.code.language.HighlightUtil;
import com.tyron.code.ui.editor.impl.text.rosemoe.CodeEditorView;
import com.tyron.editor.Editor;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.analysis.StyleReceiver;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import org.eclipse.tm4e.core.internal.theme.raw.IRawTheme;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import io.github.rosemoe.sora.lang.brackets.BracketsProvider;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import org.eclipse.tm4e.core.internal.theme.Theme;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;
import org.eclipse.tm4e.core.grammar.IGrammar;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;

public abstract class DiagnosticTextmateAnalyzer extends BaseTextmateAnalyzer {

  protected List<DiagnosticWrapper> mDiagnostics = new ArrayList<>();
  private boolean mShouldAnalyzeInBg;
  private ContentReference ref;
  protected Editor mEditor;

  private final Consumer<Styles> mStyleModifier;

  public DiagnosticTextmateAnalyzer(
      Editor editor,
      EmptyTextMateLanguage language,
      IGrammar grammar,
      LanguageConfiguration languageConfiguration,
      ThemeRegistry theme)
      throws Exception {
    super(language, grammar, languageConfiguration, theme);
    mEditor = editor;
    mStyleModifier = this::modifyStyles;
  }


  protected void modifyStyles(Styles styles) {
    if (styles == null) {
      return;
    }
    HighlightUtil.clearDiagnostics(styles);
    HighlightUtil.markDiagnostics(mEditor, mDiagnostics, styles);
  }

  public void setDiagnostics(
      CodeEditorView codeEditorView, @NonNull List<DiagnosticWrapper> diagnostics) {
    mDiagnostics = diagnostics;
  }

  @Override
  public void setReceiver(@Nullable StyleReceiver receiver) {
    if (receiver != null) {
      super.setReceiver(new StyleReceiverInterceptor(receiver, mStyleModifier));
    } else {
      super.setReceiver(null);
    }
  }

  @Override
  public void insert(CharPosition start, CharPosition end, CharSequence insertedText) {
    super.insert(start, end, insertedText);

    if (getExtraArguments().getBoolean("bg", false)) {
      if (!mShouldAnalyzeInBg) {
        mShouldAnalyzeInBg = true;
      } else {
        analyzeInBackground(ref.getReference());
      }
    }

    if (start.getLine() != end.getLine()) {
      DiagnosticSpanMapUpdater.shiftDiagnosticsOnMultiLineInsert(mDiagnostics, ref, start, end);
    } else {
      DiagnosticSpanMapUpdater.shiftDiagnosticsOnSingleLineInsert(mDiagnostics, ref, start, end);
    }
  }

  @Override
  public void delete(CharPosition start, CharPosition end, CharSequence deletedText) {
    super.delete(start, end, deletedText);

    if (getExtraArguments().getBoolean("bg", false)) {
      if (!mShouldAnalyzeInBg) {
        mShouldAnalyzeInBg = true;
      } else {
        analyzeInBackground(ref.getReference());
      }
    }

    if (start.getLine() != end.getLine()) {
      DiagnosticSpanMapUpdater.shiftDiagnosticsOnMultiLineDelete(mDiagnostics, ref, start, end);
    } else {
      DiagnosticSpanMapUpdater.shiftDiagnosticsOnSingleLineDelete(mDiagnostics, ref, start, end);
    }
  }

  @Override
  public void reset(@NonNull ContentReference content, @NonNull Bundle extraArguments) {
    // it CAN be null.
    //noinspection ConstantConditions
    if (extraArguments == null) {
      extraArguments = new Bundle();
    }
    this.ref = content;
    super.reset(content, extraArguments);
  }

  @Override
  public Bundle getExtraArguments() {
    Bundle extraArguments = super.getExtraArguments();
    if (extraArguments == null) {
      extraArguments = new Bundle();
    }
    return extraArguments;
  }

  @Override
  public void destroy() {
    mEditor = null;
    super.destroy();
  }

  public abstract void analyzeInBackground(CharSequence contents);

  public void rerunWithoutBg() {
    mShouldAnalyzeInBg = false;
    super.rerun();
  }

  public void rerunWithBg() {
    super.rerun();

    analyzeInBackground(ref.getReference());
  }

  public static class StyleReceiverInterceptor implements StyleReceiver {

    private final StyleReceiver mReceiver;
    private final Consumer<Styles> mConsumer;

    public StyleReceiverInterceptor(
        @NonNull StyleReceiver base, @NonNull Consumer<Styles> consumer) {
      mReceiver = base;
      mConsumer = consumer;
    }
    @Override 
    public void setStyles(@NonNull AnalyzeManager sourceManager, @Nullable Styles styles, @Nullable Runnable action){}
    @Override 
    public void updateBracketProvider(@NonNull AnalyzeManager sourceManager, @Nullable BracketsProvider provider){}
    @Override 
    public void setDiagnostics(@NonNull AnalyzeManager sourceManager, @Nullable DiagnosticsContainer diagnostics){
      
    }
 
    @Override
    public void setStyles(AnalyzeManager sourceManager, Styles styles) {
      mConsumer.accept(styles);
      mReceiver.setStyles(sourceManager, styles);
    }
  }
}
