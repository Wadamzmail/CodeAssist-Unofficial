package com.tyron.completion.lsp.api;

public interface LspLanguage {

  void setLanguageServer(ILanguageServer server);

  ILanguageServer getLanguageServer();
}
