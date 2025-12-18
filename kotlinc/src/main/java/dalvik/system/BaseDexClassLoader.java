package dalvik.system;

import java.io.File;
import java.net.URL;
import java.util.Enumeration;

public class BaseDexClassLoader extends ClassLoader {

  public BaseDexClassLoader(
      String dexPath, File optimizedDirectory, String libraryPath, ClassLoader parent) {
    super(parent);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    return null;
  }

  @Override
  protected URL findResource(String name) {
    return null;
  }

  @Override
  protected Enumeration<URL> findResources(String name) {
    return null;
  }

  @Override
  public String findLibrary(String name) {
    return null;
  }

  @Override
  protected synchronized Package getPackage(String name) {
    return null;
  }

  @Override
  public String toString() {
    return null;
  }
}
