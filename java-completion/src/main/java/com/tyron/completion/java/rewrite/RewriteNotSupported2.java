package com.tyron.completion.java.rewrite;

import com.tyron.completion.java.provider.JavacUtilitiesProvider;
import com.tyron.completion.model.TextEdit;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class RewriteNotSupported2 implements JavaRewrite2 {
  @Override
  public Map<Path, TextEdit[]> rewrite(JavacUtilitiesProvider task) {
    return Collections.emptyMap();
  }
}
