package com.tyron.builder.compiler.dex;

import com.android.tools.r8.ClassConflictResolver;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.tyron.builder.log.ILogger;
import java.util.Collection;
import java.util.Objects;

/*
 * @author Wadamzmail
 * special thanks to TK Studio
 */
public class MyClassConflictResolver implements ClassConflictResolver {

  private ILogger log;

  public MyClassConflictResolver(ILogger log) {
    this.log = log;
  }

  @Override
  public Origin resolveDuplicateClass(
      ClassReference reference, Collection<Origin> origins, DiagnosticsHandler handler) {

    //    warning("Duplicate class found: " + reference.getDescriptor()+"\n in Origins: "+origins);
    return origins.iterator().next();
  }

  private void warning(String message) {
    Objects.requireNonNull(log).warning(message);
  }
}
