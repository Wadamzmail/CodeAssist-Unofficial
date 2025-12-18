package dalvik.system;

import java.io.File;

public class DexClassLoader extends BaseDexClassLoader {

  public DexClassLoader(
      String dexPath, String optimizedDirectory, String libraryPath, ClassLoader parent) {
    super(dexPath, new File(optimizedDirectory), libraryPath, parent);
  }
}
