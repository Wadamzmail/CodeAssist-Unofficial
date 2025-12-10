package com.tyron.code.language.java;

import com.sun.tools.javac.code.Symbol;
import com.tyron.code.analyzer.semantic.TokenType;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import org.eclipse.tm4e.core.internal.grammar.ScopeStack;

public class JavaTokenTypes {

  public static final ScopeStack FIELD = ScopeStack.from("variable.other.object.property.java");
  public static final ScopeStack CONSTANT = ScopeStack.from("variable.other.constant");
  public static final ScopeStack PARAMETER = ScopeStack.from("variable.parameter");
  public static final ScopeStack CLASS = ScopeStack.from("entity.name.type.class");
  public static final ScopeStack METHOD_CALL = ScopeStack.from("meta.method-call");
  public static final ScopeStack METHOD_DECLARATION =
      ScopeStack.from("entity.name.function.member");
  public static final ScopeStack VARIABLE = ScopeStack.from("entity.name.variable");
  public static final ScopeStack CONSTRUCTOR = ScopeStack.from("class.instance.constructor");
  public static final ScopeStack ANNOTATION = ScopeStack.from("storage.type.annotation");

  public static ScopeStack getApplicableType(Element element) {
    if (element == null) {
      return null;
    }

    switch (element.getKind()) {
      case LOCAL_VARIABLE:
        Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) element;
        if (varSymbol.getModifiers().contains(Modifier.FINAL)) {
          return CONSTANT;
        }
        return VARIABLE;
      case METHOD:
        Symbol.MethodSymbol methodSymbol = ((Symbol.MethodSymbol) element);
        if (methodSymbol.isConstructor()) {
          return getApplicableType(methodSymbol.getEnclosingElement());
        }
        return METHOD_DECLARATION;
      case FIELD:
        VariableElement variableElement = ((VariableElement) element);
        if (variableElement.getModifiers().contains(Modifier.FINAL)) {
          return CONSTANT;
        }
        return FIELD;
      case CLASS:
        return CLASS;
      case CONSTRUCTOR:
        return CONSTRUCTOR;
      case PARAMETER:
        return PARAMETER;
      case ANNOTATION_TYPE:
        return ANNOTATION;
      default:
        return null;
    }
  }
}
