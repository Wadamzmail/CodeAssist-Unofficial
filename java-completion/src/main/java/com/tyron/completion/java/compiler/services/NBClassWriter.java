package com.tyron.completion.java.compiler.services;

import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.ClassWriter;
import com.sun.tools.javac.util.Context;

/**
 * @author lahvac
 */
public class NBClassWriter extends ClassWriter {

  public static void preRegister(Context context) {
    context.put(classWriterKey, (Context.Factory<ClassWriter>) NBClassWriter::new);
  }

  private final NBNames nbNames;
  private final Types types;

  protected NBClassWriter(Context context) {
    super(context);
    nbNames = NBNames.instance(context);
    types = Types.instance(context);
  }
}
