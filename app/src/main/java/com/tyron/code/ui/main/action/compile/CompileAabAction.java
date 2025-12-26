package com.tyron.code.ui.main.action.compile;

import android.content.Context;
import androidx.annotation.NonNull;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.Presentation;
import com.tyron.builder.compiler.BuildType;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.Module;
import com.tyron.code.ui.main.CompileCallback;
import com.tyron.code.ui.main.MainFragment;
import com.tyron.code.ui.main.MainViewModel;
import com.tyron.fileeditor.api.FileEditor;
import dev.mutwakil.codeassist.R;
import java.util.List;

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
    if (module == null) return;
    List<String> plugins = module.getPlugins();
    if (plugins == null) return;
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
