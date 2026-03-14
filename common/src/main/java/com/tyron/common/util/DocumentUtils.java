package com.tyron.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import kotlin.io.path.PathsKt;

/**
 * @author Akash Yadav
 */
public class DocumentUtils {

  public static boolean isJavaFile(Path file) {
    final var name = PathsKt.getName(file);
    final var extension = PathsKt.getExtension(file);

    return extension.equals("java")
        && Files.exists(file)
        && !Files.isDirectory(file)
        && !name.equals("module-info.java")
        && !name.equals("package-info.java");
  }

  public static boolean isXmlFile(Path file) {
    return PathsKt.getExtension(file).equals("xml")
        && Files.exists(file)
        && !Files.isDirectory(file);
  }

  public static boolean isKotlinFile(Path file) {
    if (file == null) {
      return false;
    }
    final var extension = PathsKt.getExtension(file);
    return (extension.equals("kt") || extension.equals("kts"))
        && Files.exists(file)
        && !Files.isDirectory(file);
  }

  public static boolean isSameFile(Path first, Path second) {
    try {
      return Files.isSameFile(first, second);
    } catch (IOException e) {
      return false;
    }
  }
}
