package com.tyron.completion.xml.action.context;

import static com.tyron.completion.xml.util.XmlUtils.newPullParser;

import androidx.annotation.NonNull;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.AnAction;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.Presentation;
import com.tyron.builder.util.CharSequenceReader;
import com.tyron.common.ApplicationProvider;
import com.tyron.common.util.Decompress;
import com.tyron.completion.java.rewrite.JavaRewrite;
import com.tyron.completion.util.RewriteUtil;
import com.tyron.completion.xml.R;
import com.tyron.completion.xml.XmlCompletionModule;
import com.tyron.completion.xml.rewrite.AddPermissions;
import com.tyron.completion.xml.util.XmlUtils;
import com.tyron.editor.Editor;
import com.tyron.fileeditor.api.FileEditor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class AndroidManifestAddPermissionsAction extends AnAction {

  public static final String ID = "androidManifestAddPermissionAction";

  private static final String MANIFEST_FILE_NAME = "AndroidManifest.xml";

  private static File getOrExtractPermissions() {
    File filesDir = XmlCompletionModule.getContext().getFilesDir();
    File check = new File(filesDir, "sources/android-36/data/permissions.txt");
    if (check.exists()) {
      return check;
    }
    File dest = new File(filesDir, "sources");
    Decompress.unzipFromAssets(
        ApplicationProvider.getApplicationContext(), "android-xml.zip", dest.getAbsolutePath());
    return check;
  }

  @Override
  public void update(@NonNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setVisible(false);

    if (!ActionPlaces.EDITOR.equals(event.getPlace())) {
      return;
    }

    FileEditor fileEditor = event.getData(CommonDataKeys.FILE_EDITOR_KEY);
    if (fileEditor == null) return;
    if (fileEditor.getFile() == null) return;
    if (fileEditor.getEditor() == null) return;
    Editor editor = fileEditor.getEditor();
    File file = fileEditor.getFile();
    if (!MANIFEST_FILE_NAME.equals(file.getName())) return;

    XmlPullParser parser;
    try {
      parser = newPullParser();
      parser.setInput(new CharSequenceReader(editor.getContent()));
    } catch (XmlPullParserException e) {
      return;
    }
    int depth = XmlUtils.getDepthAtPosition(parser, editor.getCaret().getStartLine() - 1);
    if (depth != 1) { // depth = 1 means we're right under the <manifest> tag
      return;
    }

    presentation.setVisible(true);
    presentation.setText(event.getDataContext().getString(R.string.menu_quickfix_add_permissions));
  }

  @Override
  public void actionPerformed(@NonNull AnActionEvent event) {
    FileEditor fileEditor = event.getData(CommonDataKeys.FILE_EDITOR_KEY);
    if (fileEditor == null) return;
    if (fileEditor.getFile() == null) return;
    if (fileEditor.getEditor() == null) return;
    Editor editor = fileEditor.getEditor();
    File file = fileEditor.getFile();

    List<String> permissions;
    try {
      permissions = FileUtils.readLines(getOrExtractPermissions(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return;
    }

    for (String usedPerm : AddPermissions.getUsedPermissions(editor.getContent())) {
      permissions.remove(usedPerm);
    }

    boolean[] selectedPermissions = new boolean[permissions.size()];

    new MaterialAlertDialogBuilder(event.getDataContext())
        .setTitle(event.getDataContext().getString(R.string.menu_quickfix_add_permissions))
        .setMultiChoiceItems(
            permissions.toArray(new String[0]),
            selectedPermissions,
            (dialog, which, isChecked) -> {
              selectedPermissions[which] = isChecked;
            })
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(
            android.R.string.ok,
            (dialog, which) -> {
              List<String> selected = new ArrayList<>();

              for (int i = 0; i < permissions.size(); i++) {
                if (selectedPermissions[i]) {
                  selected.add(permissions.get(i));
                }
              }

              JavaRewrite rewrite =
                  new AddPermissions(file.toPath(), editor.getContent(), selected);
              RewriteUtil.performRewrite(editor, file, null, rewrite);
            })
        .show();
  }
}
