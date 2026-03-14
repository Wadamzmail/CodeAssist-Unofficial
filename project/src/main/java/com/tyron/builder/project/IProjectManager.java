package com.tyron.builder.project;

import java.io.File;

public class IProjectManager {

  public volatile Project currentProject;
  private static IProjectManager instance;

  public static IProjectManager getInstance() {
    if (instance == null) {
      return instance = new IProjectManager();
    }
    return instance;
  }

  public File getProjectDir() {
    if (currentProject == null) return null;
    return currentProject.getRootFile();
  }

  public String getProjectDirPath() {
    if (getProjectDir() == null) return null;
    return getProjectDir().getPath();
  }
}
