package com.tyron.editor.event;

import java.util.EventListener;
import org.jetbrains.annotations.NotNull;

public interface ContentListener extends EventListener {
  ContentListener[] EMPTY_ARRAY = new ContentListener[0];

  default void beforeContentChanged(@NotNull ContentEvent event) {}

  default void contentChanged(@NotNull ContentEvent event) {}
}
