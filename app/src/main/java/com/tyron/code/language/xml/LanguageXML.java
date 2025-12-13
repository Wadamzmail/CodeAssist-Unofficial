package com.tyron.code.language.xml;

import android.content.res.AssetManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.tyron.builder.compiler.manifest.xml.XmlFormatPreferences;
import com.tyron.builder.compiler.manifest.xml.XmlFormatStyle;
import com.tyron.builder.compiler.manifest.xml.XmlPrettyPrinter;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.analyzer.BaseTextmateAnalyzer;
import com.tyron.code.ui.editor.impl.text.rosemoe.CodeEditorView;
import com.tyron.code.util.ProjectUtils;
import com.tyron.completion.xml.lexer.XMLLexer;
import com.tyron.editor.Editor;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException;
import io.github.rosemoe.sora.lang.completion.CompletionHelper;
import io.github.rosemoe.sora.lang.completion.CompletionItem;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.text.TextUtils;
import io.github.rosemoe.sora.util.MyCharacter;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import io.github.rosemoe.sora.text.TextRange;
import io.github.rosemoe.sora.lang.format.AsyncFormatter;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.text.Content;
import androidx.annotation.Nullable;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import com.tyron.code.language.LanguageManager;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import org.eclipse.tm4e.core.internal.theme.Theme;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;
import org.eclipse.tm4e.core.grammar.IGrammar;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
//new 
import com.tyron.completion.xml.task.InjectResourcesTask; 
import com.tyron.language.api.CodeAssistLanguage;
import com.tyron.viewbinding.task.InjectViewBindingTask;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.completion.xml.v2.AndroidXmlCompletionProvider;
import com.tyron.completion.xml.v2.events.XmlResourceChangeEvent;
import com.tyron.code.language.CompletionItemWrapper;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.completion.CompletionParameters;
import com.tyron.builder.project.api.Module;
import com.tyron.code.event.EventManager;

public class LanguageXML extends EmptyTextMateLanguage implements Language, CodeAssistLanguage{

  private final Editor mEditor;

  private final BaseTextmateAnalyzer mAnalyzer;
//  private final TextMateLanguage delegate;
  private final Formatter formatter = new AsyncFormatter() {
        @Nullable
        @Override
        public TextRange formatAsync(@NonNull Content text, @NonNull TextRange cursorRange) {
          XmlFormatPreferences preferences = XmlFormatPreferences.defaults();
    File file = mEditor.getCurrentFile();
    CharSequence formatted = null;
    if ("AndroidManifest.xml".equals(file.getName())) {
      formatted =
          XmlPrettyPrinter.prettyPrint(
              String.valueOf(text.toString()), preferences, XmlFormatStyle.MANIFEST, "\n");
    } else {
      if (ProjectUtils.isLayoutXMLFile(file)) {
        formatted =
            XmlPrettyPrinter.prettyPrint(
                String.valueOf(text), preferences, XmlFormatStyle.LAYOUT, "\n");
      } else if (ProjectUtils.isResourceXMLFile(file)) {
        formatted =
            XmlPrettyPrinter.prettyPrint(
                String.valueOf(text), preferences, XmlFormatStyle.RESOURCE, "\n");
      }
    }
    if (formatted == null) {
      formatted = text.toString();
    } 
            if (!text.toString().equals(formatted)) {
            int oldCursor = cursorRange.getStartIndex();
            text.delete(0, text.length());
            text.insert(0, 0, formatted);
            int newCursor = Math.min(oldCursor, formatted.length());
            CharPosition pos = text.getIndexer().getCharPosition(newCursor);
            return new TextRange(pos, pos);
            }
            return cursorRange;
        }

        @Nullable
        @Override
        public TextRange formatRegionAsync(@NonNull Content text,
                                           @NonNull TextRange rangeToFormat,
                                           @NonNull TextRange cursorRange) {
            return null;
        }
    };
@NonNull
    @Override
    public Formatter getFormatter() {
        return formatter;
    }

  public LanguageXML(Editor editor) {
    mEditor = editor;

    try {
      AssetManager assetManager = ApplicationLoader.applicationContext.getAssets();
     // delegate = LanguageManager.createTextMateLanguage("text.xml");
      mAnalyzer = XMLAnalyzer.create(editor, this) ;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isAutoCompleteChar(char ch) {
    return MyCharacter.isJavaIdentifierPart(ch) || ch == '<' || ch == '/' || ch == ':' || ch == '.';
  }

  @Override
  public boolean useTab() {
    return true;
  }

  
  public CharSequence format(CharSequence text) {
    XmlFormatPreferences preferences = XmlFormatPreferences.defaults();
    File file = mEditor.getCurrentFile();
    CharSequence formatted = null;
    if ("AndroidManifest.xml".equals(file.getName())) {
      formatted =
          XmlPrettyPrinter.prettyPrint(
              String.valueOf(text), preferences, XmlFormatStyle.MANIFEST, "\n");
    } else {
      if (ProjectUtils.isLayoutXMLFile(file)) {
        formatted =
            XmlPrettyPrinter.prettyPrint(
                String.valueOf(text), preferences, XmlFormatStyle.LAYOUT, "\n");
      } else if (ProjectUtils.isResourceXMLFile(file)) {
        formatted =
            XmlPrettyPrinter.prettyPrint(
                String.valueOf(text), preferences, XmlFormatStyle.RESOURCE, "\n");
      }
    }
    if (formatted == null) {
      formatted = text;
    }
    return formatted;
  }

  @Override
  public SymbolPairMatch getSymbolPairs() {
    //return delegate.getSymbolPairs();
    return new SymbolPairMatch.DefaultSymbolPairs();
  }

  @Override
  public NewlineHandler[] getNewlineHandlers() {
    return new NewlineHandler[] {
      new StartTagHandler(), new EndTagHandler(), new EndTagAttributeHandler()
    };
  }

  @Override
  public void destroy() {
   // delegate.destroy();
    mAnalyzer.destroy();
  }

  @NonNull
  @Override
  public AnalyzeManager getAnalyzeManager() {
    //return delegate.getAnalyzeManager();
    return mAnalyzer;
  }

  @Override
  public int getInterruptionLevel() {
    return INTERRUPTION_LEVEL_SLIGHT;
  }

  @Override
  public void requireAutoComplete(
      @NonNull ContentReference content,
      @NonNull CharPosition position,
      @NonNull CompletionPublisher publisher,
      @NonNull Bundle extraArguments)
      throws CompletionCancelledException {
    if (mEditor.getProject() == null) {
            return;
        }
        Module module = mEditor.getProject().getModule(mEditor.getCurrentFile());
        if (!(module instanceof AndroidModule)) {
            return;
        }
        String prefix = CompletionHelper.computePrefix(content, position, this::isAutoCompleteChar);
        CompletionParameters parameters = CompletionParameters.builder()
                .setPrefix(prefix)
                .setModule(module)
                .setProject(mEditor.getProject())
                .setFile(mEditor.getCurrentFile())
                .setIndex(position.getIndex())
                .setLine(position.getLine())
                .setColumn(position.getColumn())
                .setContents(content.getReference().toString())
                .build();
        CompletionList items =
                new AndroidXmlCompletionProvider().complete(parameters);
        if (items == null) {
            return;
        }
        for (CompletionItem item : items.getItems()) {
            publisher.addItem(new CompletionItemWrapper(item));
        }
  }
  
  @Override
    public void onContentChange(File file, CharSequence contents) {
        if (mEditor.getProject() == null) {
            return;
        }
        AndroidModule mainModule = (AndroidModule) mEditor.getProject().getMainModule();
       try{ InjectResourcesTask.inject(mEditor.getProject());}catch(Exception e){}
       try{ InjectViewBindingTask.inject(mEditor.getProject(),mainModule);}catch(Exception e){}
    }

  @Override
  public int getIndentAdvance(@NonNull ContentReference content, int line, int column) {
    String text = content.getLine(line).substring(0, column);
    return getIndentAdvance(text);
  }

  public int getIndentAdvance(String content) {
    return getIndentAdvance(content, XMLLexer.DEFAULT_MODE, true);
  }

  public int getIndentAdvance(String content, int mode, boolean ignore) {
    return 0;
    //        XMLLexer lexer = new XMLLexer(CharStreams.fromString(content));
    //        lexer.pushMode(mode);
    //
    //        int advance = 0;
    //        while (lexer.nextToken()
    //                       .getType() != Lexer.EOF) {
    //            switch (lexer.getToken()
    //                    .getType()) {
    //                case XMLLexer.OPEN:
    //                    advance++;
    //                    break;
    //                case XMLLexer.CLOSE:
    //                case XMLLexer.SLASH_CLOSE:
    //                    advance--;
    //                    break;
    //            }
    //        }
    //
    //        if (advance == 0 && mode != XMLLexer.INSIDE) {
    //            return getIndentAdvance(content, XMLLexer.INSIDE, ignore);
    //        }
    //
    //        return advance * mEditor.getTabCount();
  }

  public int getFormatIndent(String line) {
    return getIndentAdvance(line, XMLLexer.DEFAULT_MODE, false);
  }

  private class EndTagHandler implements NewlineHandler {

    @Override
    public boolean matchesRequirement(@NonNull Content text,
                                      @NonNull CharPosition position,
                                      @Nullable Styles style) {
        int line = position.line;
        if (line < 0 || line >= text.getLineCount()) return false;

        String before = text.subContent(line, 0, line, position.column).toString();
        String after  = text.subContent(line, position.column,
                                        line, text.getLine(line).length()).toString();

        String trimmedBefore = before.trim();
        return trimmedBefore.startsWith("<")
                && trimmedBefore.endsWith(">")
                && after.trim().startsWith("</");
    }

    @Override
    @NonNull
    public NewlineHandleResult handleNewline(@NonNull Content text,
                                             @NonNull CharPosition position,
                                             @Nullable Styles style,
                                             int tabSize) {
        int line = position.line;
        String before = text.subContent(line, 0, line, position.column).toString();

        int indent = TextUtils.countLeadingSpaceCount(before, tabSize);
        String bodyLine  = TextUtils.createIndent(indent + tabSize, tabSize, false);
        String closeLine = TextUtils.createIndent(indent, tabSize, false);

        StringBuilder sb = new StringBuilder("\n")
                .append(bodyLine)
                .append('\n')
                .append(closeLine);

        int cursorShift = closeLine.length() + 1;
        return new NewlineHandleResult(sb, cursorShift);
    }
}


  private class EndTagAttributeHandler implements NewlineHandler {

    @Override
    public boolean matchesRequirement(@NonNull Content text,
                                      @NonNull CharPosition position,
                                      @Nullable Styles style) {
        int line = position.line;
        if (line < 0 || line >= text.getLineCount()) return false;

        String before = text.subContent(line, 0, line, position.column).toString();
        String after  = text.subContent(line, position.column,
                                        line, text.getLine(line).length()).toString();
        return before.trim().endsWith(">") && after.trim().startsWith("</");
    }

    @Override
    @NonNull
    public NewlineHandleResult handleNewline(@NonNull Content text,
                                             @NonNull CharPosition position,
                                             @Nullable Styles style,
                                             int tabSize) {
        int line = position.line;
        String before = text.subContent(line, 0, line, position.column).toString();

        int indent = TextUtils.countLeadingSpaceCount(before, tabSize);
        String indentInner = TextUtils.createIndent(indent, tabSize, false);
        String indentClose = TextUtils.createIndent(Math.max(0, indent - tabSize), tabSize, false);

        StringBuilder sb = new StringBuilder("\n")
                .append(indentInner)
                .append('\n')
                .append(indentClose);

        int cursorShift = indentClose.length() + 1; // +1 for the newline
        return new NewlineHandleResult(sb, cursorShift);
    }
}


  private class StartTagHandler implements NewlineHandler {

    @Override
    public boolean matchesRequirement(@NonNull Content text,
                                      @NonNull CharPosition position,
                                      @Nullable Styles style) {
        int line = position.line;
        if (line < 0 || line >= text.getLineCount()) return false;

        String before = text.subContent(line, 0, line, position.column).toString();
        String trimmed = before.trim();
        return trimmed.startsWith("<") && !trimmed.endsWith(">");
    }

    @Override
    @NonNull
    public NewlineHandleResult handleNewline(@NonNull Content text,
                                             @NonNull CharPosition position,
                                             @Nullable Styles style,
                                             int tabSize) {
        int line = position.line;
        String before = text.subContent(line, 0, line, position.column).toString();

        int indent = TextUtils.countLeadingSpaceCount(before, tabSize);
        String indentStr = TextUtils.createIndent(indent + tabSize, tabSize, false);

        return new NewlineHandleResult(new StringBuilder("\n").append(indentStr), 0);
    }
}

}
