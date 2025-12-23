package com.tyron.fileeditor.api;

import com.tyron.editor.Content;
import com.tyron.editor.Editor;

public interface TextEditor extends FileEditor {

    Content getContent();
    Editor getEditor();
}
