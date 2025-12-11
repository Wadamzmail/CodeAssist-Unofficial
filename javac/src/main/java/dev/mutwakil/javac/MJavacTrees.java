package dev.mutwakil.javac;

import javax.tools.JavaCompiler;
import com.sun.tools.javac.api.JavacTrees;

public class MJavacTrees{
    public static JavacTrees instance(JavaCompiler.CompilationTask task) {
         JavacTrees.instance(task);
    }
}