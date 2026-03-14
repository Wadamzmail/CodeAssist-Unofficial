package com.tyron.code.util;

import android.util.Log;
import com.tyron.builder.model.DiagnosticWrapper;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.tools.Diagnostic;

/*
 * @author Wadamzmail
 */
public final class DiagnosticsUtils {

  private static final String TAG = "DiagnosticsUtils";

  private DiagnosticsUtils() {}

  @SuppressWarnings("unchecked")
  public static List<DiagnosticWrapper> toWrappers(DiagnosticsContainer container) {
    if (container == null) return Collections.emptyList();

    try {
      Field regionsField = DiagnosticsContainer.class.getDeclaredField("regions");
      regionsField.setAccessible(true);

      List<DiagnosticRegion> regions = (List<DiagnosticRegion>) regionsField.get(container);

      if (regions == null || regions.isEmpty()) {
        return Collections.emptyList();
      }

      List<DiagnosticWrapper> result = new ArrayList<>(regions.size());

      for (DiagnosticRegion region : regions) {
        DiagnosticWrapper wrapper = new DiagnosticWrapper();

        wrapper.setStartPosition(region.startIndex);
        wrapper.setEndPosition(region.endIndex);
        wrapper.setPosition(region.startIndex);

        wrapper.setKind(mapSeverity(region.severity));

        DiagnosticDetail detail = region.detail;
        if (detail != null) {
          wrapper.setMessage(
              detail.getDetailedMessage() != null
                  ? detail.getDetailedMessage()
                  : detail.getBriefMessage());

          wrapper.setExtra(detail);
        }

        result.add(wrapper);
      }

      return result;

    } catch (Throwable e) {
      Log.e(TAG, "Failed to Convert Diagnostics", e);
      return Collections.emptyList();
    }
  }

  private static Diagnostic.Kind mapSeverity(short severity) {
    switch (severity) {
      case DiagnosticRegion.SEVERITY_ERROR:
        return Diagnostic.Kind.ERROR;
      case DiagnosticRegion.SEVERITY_WARNING:
        return Diagnostic.Kind.WARNING;
      case DiagnosticRegion.SEVERITY_TYPO:
        return Diagnostic.Kind.MANDATORY_WARNING;
      case DiagnosticRegion.SEVERITY_NONE:
      default:
        return Diagnostic.Kind.NOTE;
    }
  }
}
