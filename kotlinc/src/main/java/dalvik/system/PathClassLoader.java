package dalvik.system;

public class PathClassLoader extends BaseDexClassLoader {
  public PathClassLoader(String dexPath, ClassLoader parent) {
    super(dexPath, null, null, parent);
  }

  public PathClassLoader(String dexPath, String libraryPath, ClassLoader parent) {
    super(dexPath, null, libraryPath, parent);
  }
}
