package com.tyron.code.analyzer.semantic;

import androidx.annotation.NonNull;
import org.eclipse.tm4e.core.internal.grammar.ScopeStack;

public class SemanticToken {
  private final ScopeStack tokenType;
  private final int tokenModifiers;
  private final int offset;
  private final int length;

  public SemanticToken(int offset, int length, ScopeStack tokenType, int tokenModifiers) {
    this.offset = offset;
    this.length = length;
    this.tokenType = tokenType;
    this.tokenModifiers = tokenModifiers;
  }

  public ScopeStack getTokenType() {
    return tokenType;
  }

  public int getTokenModifiers() {
    return tokenModifiers;
  }

  public int getOffset() {
    return offset;
  }

  public int getLength() {
    return length;
  }

  @NonNull
  @Override
  public String toString() {
    return "SemanticToken{"
        + "tokenType="
        + tokenType
        + ", tokenModifiers="
        + tokenModifiers
        + ", offset="
        + offset
        + ", length="
        + length
        + '}';
  }
}
