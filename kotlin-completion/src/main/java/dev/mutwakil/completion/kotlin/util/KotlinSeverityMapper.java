package dev.mutwakil.completion.kotlin.util;

import javax.tools.Diagnostic;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;

/*
 * @author Wadamzmail
 */
public final class KotlinSeverityMapper {

  private KotlinSeverityMapper() {}

  public static Diagnostic.Kind toKind(CompilerMessageSeverity severity) {
    if (severity == null) {
      return Diagnostic.Kind.NOTE;
    }

    switch (severity) {
      case EXCEPTION:
      case ERROR:
        return Diagnostic.Kind.ERROR;

      case STRONG_WARNING:
      case FIXED_WARNING:
      case WARNING:
        return Diagnostic.Kind.WARNING;

      case INFO:
      case LOGGING:
      case OUTPUT:
      default:
        return Diagnostic.Kind.NOTE;
    }
  }
}
