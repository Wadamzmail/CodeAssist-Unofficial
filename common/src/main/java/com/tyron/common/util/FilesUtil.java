package com.tyron.common.util;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class FilesUtil {
  private FilesUtil() {}

  public static synchronized boolean changed(Set<File> oldFiles, Set<File> newFiles) {
    if (oldFiles.size() != newFiles.size()) {
      return true;
    }

    for (File oldFile : oldFiles) {
      if (!newFiles.contains(oldFile)) {
        return true;
      }
    }

    for (File newFile : newFiles) {
      if (!oldFiles.contains(newFile)) {
        return true;
      }
    }

    return false;
  }

  public static Set<File> getFiles(File dir, String ext) {
    Set<File> Files = new HashSet<>();

    File[] files = dir.listFiles();
    if (files == null) {
      return Collections.emptySet();
    }

    for (File file : files) {
      if (file.isDirectory()) {
        Files.addAll(getFiles(file, ext));
      } else {
        if (file.getName().endsWith(ext)) {
          Files.add(file);
        }
      }
    }

    return Files;
  }
}
