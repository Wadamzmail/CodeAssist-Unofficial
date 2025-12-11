package dev.mutwakil.javac;

import javax.tools.JavaCompiler;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.util.Context;

public class MJavacTrees{
    public static JavacTrees instance(JavaCompiler.CompilationTask task) {
         return JavacTrees.instance(task);
    }
    public static JavacTrees instance(Context context) {
        return JavacTrees.instance(context);
    } 
}