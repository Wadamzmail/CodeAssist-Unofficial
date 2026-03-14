package com.tyron.completion.model;

import com.google.common.collect.ImmutableList;
import com.tyron.completion.CompletionPrefixMatcher;
import com.tyron.completion.DefaultInsertHandler;
import com.tyron.completion.InsertHandler;
import com.tyron.completion.drawable.CircleDrawable;
import com.tyron.completion.util.CompletionUtils;
import com.tyron.editor.Editor;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** A class representing the completion item shown in the user list */
public class CompletionItem extends io.github.rosemoe.sora.lang.completion.CompletionItem
    implements Comparable<CompletionItem> {

  @SuppressWarnings("NewApi")
  public static final Comparator<CompletionItem> COMPARATOR =
      Comparator.comparing(
              (CompletionItem item) -> item.getMatchLevel().ordinal(), Comparator.reverseOrder())
          .thenComparing(CompletionItem::getSortText)
          .thenComparing(
              it -> it.getFilterTexts().isEmpty() ? it.getLabel() : it.getFilterTexts().get(0));

  public static CompletionItem create(String label, String desc, String commitText) {
    return create(label, desc, commitText, null);
  }

  public static CompletionItem create(
      String label, String desc, String commitText, DrawableKind kind) {
    CompletionItem completionItem = new CompletionItem(label, desc, commitText, kind);
    completionItem.sortText = "";
    completionItem.matchLevel = CompletionPrefixMatcher.MatchLevel.NOT_MATCH;
    return completionItem;
  }

  private InsertHandler insertHandler;

  public String commitText;
  public Kind action = Kind.NORMAL;
  public DrawableKind iconKind = DrawableKind.Method;
  public int cursorOffset = -1;
  public List<TextEdit> additionalTextEdits;
  public String data = "";

  private List<String> filterTexts = new ArrayList<>(1);
  private CompletionPrefixMatcher.MatchLevel matchLevel;

  public CompletionItem() {
    this("");
  }

  public CompletionItem(String label) {
    this(label, "", "", DrawableKind.Method);
  }

  public CompletionItem(String label, String desc, String commitText, DrawableKind kind) {
    super(label, desc, new CircleDrawable(kind));
    this.commitText = commitText;
    this.cursorOffset = commitText.length();
    this.iconKind = kind;
    this.insertHandler = new DefaultInsertHandler(CompletionUtils.JAVA_PREDICATE, this);
    this.sortText = "";
  }

  public void setSortText(String sortText) {
    super.sortText = sortText;
  }

  public String getSortText() {
    return super.sortText;
  }

  public String getLabel() {
    return String.valueOf(super.label);
  }

  public String getDesc() {
    return String.valueOf(super.desc);
  }

  public ImmutableList<String> getFilterTexts() {
    if (filterTexts.isEmpty()) {
      return ImmutableList.of(getLabel());
    }
    return ImmutableList.copyOf(filterTexts);
  }

  public void addFilterText(String text) {
    filterTexts.add(text);
  }

  public CompletionPrefixMatcher.MatchLevel getMatchLevel() {
    return matchLevel;
  }

  public void setMatchLevel(CompletionPrefixMatcher.MatchLevel matchLevel) {
    this.matchLevel = matchLevel;
  }

  public enum Kind {
    OVERRIDE,
    IMPORT,
    NORMAL
  }

  public void setInsertHandler(InsertHandler handler) {
    this.insertHandler = handler;
  }

  @Override
  public String toString() {
    return String.valueOf(label);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CompletionItem)) {
      return false;
    }
    CompletionItem that = (CompletionItem) o;
    return cursorOffset == that.cursorOffset
        && Objects.equals(String.valueOf(label), String.valueOf(that.label))
        && Objects.equals(desc, that.desc)
        && Objects.equals(commitText, that.commitText)
        && action == that.action
        && iconKind == that.iconKind
        && Objects.equals(additionalTextEdits, that.additionalTextEdits)
        && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        label, desc, commitText, action, iconKind, cursorOffset, additionalTextEdits, data);
  }

  @Override
  public int compareTo(CompletionItem o) {
    return COMPARATOR.compare(this, o);
  }

  public void handleInsert(Editor editor) {
    insertHandler.handleInsert(editor);
  }

  public void appendDesc(String extra) {
    super.desc = String.valueOf(super.desc) + extra;
  }

  @Override
  public void performCompletion(CodeEditor editor, Content text, int line, int column) {
    if (!(editor instanceof Editor)) {
      throw new IllegalArgumentException(
          "Cannot use CompletionItem on an editor that does not implement"
              + " com.tyron.editor.Editor");
    }

    Editor rawEditor = ((Editor) editor);
    if (rawEditor == null) {
      throw new RuntimeException("Editor is null, cannot handle insert");
    }
    handleInsert(rawEditor);
  }
}
