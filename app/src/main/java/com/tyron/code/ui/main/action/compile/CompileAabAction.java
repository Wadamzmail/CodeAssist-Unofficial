package com.tyron.code.ui.main.action.compile;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.AnAction;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.Presentation;
import com.tyron.builder.compiler.BuildType;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.FileManager;
import com.tyron.builder.project.api.Module;
import com.tyron.code.ui.editor.Savable;
import com.tyron.code.ui.main.CompileCallback;
import com.tyron.code.ui.main.MainFragment;
import com.tyron.code.ui.main.MainViewModel;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.fileeditor.api.FileEditor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.codeassist.unofficial.R;
import com.tyron.editor.Editor;

public class CompileAabAction extends CompileAction {

  public CompileAabAction() {
        super(BuildType.AAB);
  }

  @Override
  public void update(@NonNull AnActionEvent event) {

    CompileCallback data = event.getData(MainFragment.COMPILE_CALLBACK_KEY);
    if (data == null) {
      event.getPresentation().setVisible(false);
      return;
    }

    Presentation presentation = event.getPresentation();
    presentation.setVisible(false);
    Context context = event.getData(CommonDataKeys.CONTEXT);
    if (!ActionPlaces.MAIN_TOOLBAR.equals(event.getPlace())) {
      return;
    }

    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    Module module = project.getMainModule();
    List<String> plugins = module.getPlugins();
    String pluginType = plugins.toString();
    if (!pluginType.contains("com.android.application")) {
      presentation.setVisible(false);
      return;
    }

    MainViewModel mainViewModel = event.getData(MainFragment.MAIN_VIEW_MODEL_KEY);
    if (mainViewModel == null) {
      return;
    }

    presentation.setVisible(true);
    presentation.setText(context.getString(R.string.menu_build_aab));
  }

  @Override
  public void actionPerformed(@NonNull AnActionEvent event) {
    super.actionPerformed(event);
    Context context = event.getData(CommonDataKeys.CONTEXT);
    CompileCallback callback = event.getData(MainFragment.COMPILE_CALLBACK_KEY);
    MainViewModel viewModel = event.getRequiredData(MainFragment.MAIN_VIEW_MODEL_KEY);
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    List<FileEditor> editors = viewModel.getFiles().getValue();
    if (editors == null) {
      return;
    }
    callback.compile(BuildType.AAB);
  }
  
  @Override
  public String getTitle(Context context) {
      return context.getString(R.string.action_menu_build_aab);
  }
   
}
