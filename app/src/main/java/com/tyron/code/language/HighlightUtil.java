package com.tyron.code.language;

import android.util.Log;

import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.editor.CharPosition;
import com.tyron.editor.Editor;

import javax.tools.Diagnostic;

import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.Spans;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora2.BuildConfig;
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs;
import io.github.rosemoe.sora.lang.styling.span.SpanExternalRenderer;

public class HighlightUtil {

    public static void replaceSpan(Styles styles, Span newSpan, int startLine, int startColumn, int endLine, int endColumn) {
        for (int line = startLine; line <= endLine; line++) {
            ProgressManager.checkCanceled();
            int start = (line == startLine ? startColumn : 0);
            int end = (line == endLine ? endColumn : Integer.MAX_VALUE);
            Spans.Reader read = styles.getSpans().read();
            List<io.github.rosemoe.sora.lang.styling.Span> spans = new ArrayList<>(read.getSpansOnLine(line));
            int increment;
            for (int i = 0; i < spans.size(); i += increment) {
                ProgressManager.checkCanceled();
                io.github.rosemoe.sora.lang.styling.Span span = spans.get(i);
                increment = 1;
                if (span.getColumn() >= end) {
                    break;
                }
                int spanEnd = (i + 1 >= spans.size() ? Integer.MAX_VALUE : spans.get(i + 1).getColumn());
                if (spanEnd >= start) {
                    int regionStartInSpan = Math.max(span.getColumn(), start);
                    int regionEndInSpan = Math.min(end, spanEnd);
                    if (regionStartInSpan == span.getColumn()) {
                        if (regionEndInSpan != spanEnd) {
                            increment = 2;
                            io.github.rosemoe.sora.lang.styling.Span nSpan = span.copy();
                            nSpan.setColumn(regionEndInSpan);
                            spans.add(i + 1, nSpan);
                        }
                        span.setUnderlineColor(newSpan.getUnderlineColor());
                        span.setStyle(newSpan.getStyle());
                        span.setSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER,(SpanExternalRenderer)newSpan.getSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER));
                    } else {
                        //regionStartInSpan > span.column
                        if (regionEndInSpan == spanEnd - 1) {
                            increment = 2;
                            io.github.rosemoe.sora.lang.styling.Span nSpan = span.copy();
                            nSpan.setColumn(regionStartInSpan);
                            spans.add(i + 1, nSpan);
                            span.setUnderlineColor(newSpan.getUnderlineColor());
                            span.setStyle(newSpan.getStyle());
                            span.setSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER,(SpanExternalRenderer)newSpan.getSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER));
                        } else {
                            increment = 3;
                            io.github.rosemoe.sora.lang.styling.Span span1 = span.copy();
                            span1.setColumn(regionEndInSpan);
                            span1.setUnderlineColor(newSpan.getUnderlineColor());
                            span1.setStyle(newSpan.getStyle());
                            span1.setSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER,newSpan.getSpanExt(SpanExtAttrs.EXT_EXTERNAL_RENDERER));
                            io.github.rosemoe.sora.lang.styling.Span span2 = span.copy();
                            span2.setColumn(regionEndInSpan);
                            spans.add(i + 1, span1);
                            spans.add(i + 2, span2);
                        }
                    }
                }
            }

            Spans.Modifier modify = styles.getSpans().modify();
            modify.setSpansOnLine(line, spans);
        }
    }

    public static void markProblemRegion(Styles styles, int newFlag, int startLine, int startColumn, int endLine, int endColumn) {
        for (int line = startLine; line <= endLine; line++) {
            ProgressManager.checkCanceled();
            int start = (line == startLine ? startColumn : 0);
            int end = (line == endLine ? endColumn : Integer.MAX_VALUE);
            Spans.Reader read = styles.getSpans().read();
            List<io.github.rosemoe.sora.lang.styling.Span> spans = new ArrayList<>(read.getSpansOnLine(line));
            int increment;
            for (int i = 0; i < spans.size(); i += increment) {
                ProgressManager.checkCanceled();
                io.github.rosemoe.sora.lang.styling.Span span = spans.get(i);
                increment = 1;
                if (span.getColumn() >= end) {
                    break;
                }
                int spanEnd = (i + 1 >= spans.size() ? Integer.MAX_VALUE : spans.get(i + 1).getColumn());
                if (spanEnd >= start) {
                    int regionStartInSpan = Math.max(span.getColumn(), start);
                    int regionEndInSpan = Math.min(end, spanEnd);
                    if (regionStartInSpan == span.getColumn()) {
                        if (regionEndInSpan != spanEnd) {
                            increment = 2;
                            io.github.rosemoe.sora.lang.styling.Span nSpan = span.copy();
                            nSpan.setColumn( regionEndInSpan);
                            spans.add(i + 1, nSpan);
                        }
                    } else {
                        //regionStartInSpan > span.column
                        if (regionEndInSpan == spanEnd) {
                            increment = 2;
                            io.github.rosemoe.sora.lang.styling.Span nSpan = span.copy();
                            nSpan.setColumn(regionStartInSpan);
                            spans.add(i + 1, nSpan);
                        } else {
                            increment = 3;
                            io.github.rosemoe.sora.lang.styling.Span span1 = span.copy();
                            span1.setColumn(regionStartInSpan);
                            io.github.rosemoe.sora.lang.styling.Span span2 = span.copy();
                            span2.setColumn(regionEndInSpan);
                            spans.add(i + 1, span1);
                            spans.add(i + 2, span2);
                        }
                    }
                }
            }

            Spans.Modifier modify = styles.getSpans().modify();
            modify.setSpansOnLine(line, spans);
        }
    }


    /**
     * Highlights the list of given diagnostics, taking care of conversion between 1-based offsets
     * to 0-based offsets.
     * It also makes the Diagnostic eligible for shifting as the user types.
     */
    public static void markDiagnostics(Editor editor, List<DiagnosticWrapper> diagnostics,
                                       Styles styles) {
        diagnostics.forEach(it -> {
            ProgressManager.checkCanceled();
            try {
                int startLine;
                int startColumn;
                int endLine;
                int endColumn;
                if (it.getPosition() != DiagnosticWrapper.USE_LINE_POS) {
                    if (it.getStartPosition() == -1) {
                        it.setStartPosition(it.getPosition());
                    }
                    if (it.getEndPosition() == -1) {
                        it.setEndPosition(it.getPosition());
                    }

                    if (it.getStartPosition() > editor.getContent().length()) {
                        return;
                    }
                    if (it.getEndPosition() > editor.getContent().length()) {
                        return;
                    }
                    CharPosition start = editor.getCharPosition((int) it.getStartPosition());
                    CharPosition end = editor.getCharPosition((int) it.getEndPosition());

                    int sLine = start.getLine();
                    int sColumn = start.getColumn();
                    int eLine = end.getLine();
                    int eColumn = end.getColumn();

                    // the editor does not support marking underline spans for the same start and end
                    // index
                    // to work around this, we just subtract one to the start index
                    if (sLine == eLine && eColumn == sColumn) {
                        sColumn--;
                        eColumn++;
                    }

                    it.setStartLine(sLine);
                    it.setEndLine(eLine);
                    it.setStartColumn(sColumn);
                    it.setEndColumn(eColumn);
                }
                startLine = it.getStartLine();
                startColumn = it.getStartColumn();
                endLine = it.getEndLine();
                endColumn = it.getEndColumn();

            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                if (BuildConfig.DEBUG) {
                    Log.d("HighlightUtil", "Failed to mark diagnostics", e);
                }
            }
        });
    }

    public static void clearDiagnostics(Styles styles) {

    }
            } 
