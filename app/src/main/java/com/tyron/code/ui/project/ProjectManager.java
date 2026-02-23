package com.tyron.code.ui.project;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.tyron.builder.compiler.BuildType;
import com.tyron.builder.compiler.incremental.resource.IncrementalAapt2Task;
import com.tyron.builder.compiler.manifest.ManifestMergeTask;
import com.tyron.builder.exception.CompilationFailedException;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.template.CodeTemplate;
import com.tyron.code.util.ProjectUtils;
import com.tyron.common.logging.IdeLog;
import com.tyron.completion.index.CompilerService;
import com.tyron.completion.java.JavaCompilerProvider;
import com.tyron.completion.java.compiler.JavaCompilerService;
import com.tyron.completion.java.provider.CompletionEngine;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.completion.xml.XmlIndexProvider;
import com.tyron.completion.xml.XmlRepository;
import com.tyron.completion.xml.task.InjectResourcesTask;
import com.tyron.viewbinding.task.InjectViewBindingTask;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
//import kotlin.collections.CollectionsKt;
import org.apache.commons.io.FileUtils;

//new
import com.tyron.completion.xml.v2.events.XmlReparsedEvent;
import com.tyron.completion.xml.v2.events.XmlResourceChangeEvent;
import com.tyron.completion.xml.v2.project.ResourceRepositoryManager; 
import java.util.function.Consumer;
import com.tyron.code.event.FileCreatedEvent;
import com.tyron.code.event.FileDeletedEvent;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import com.tyron.common.util.DebouncerStore;
import com.tyron.builder.BuildModule;
import com.tyron.completion.java.compiler.Parser;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.kotlin.completion.KotlinEnvironment;
import com.tyron.completion.java.provider.PruneMethodBodies;
import com.tyron.completion.xml.v2.LayoutRepo;
import com.tyron.builder.project.IProjectManager;
import com.tyron.completion.lsp.api.ILanguageServerRegistry;
import com.itsaky.androidide.eventbus.events.project.ProjectInitializedEvent;
import com.tyron.completion.lsp.api.DefaultLanguageServerRegistry;

public class ProjectManager {

  private static final Logger LOG = IdeLog.getCurrentLogger(ProjectManager.class);
  private Instant now;
  private SharedPreferences mPreferences;
  public static String XML="Index XML files",
    JAVA = "Index Java files", RES = "Generate Resource files",
    DOWNLOAD = "Download Libraries ",INJECT_RES="Inject Resources";
  public static final HashMap<String, Boolean> indexFiles = new HashMap<String, Boolean>(){
        {
            put(XML, true);
            put(JAVA, true);
            put(RES, true);
            put(DOWNLOAD,true);
            put(INJECT_RES,true);
        }
    };

  public interface TaskListener {
    void onTaskStarted(String message);

    void onComplete(Project project, boolean success, String message);
  }

  public interface OnProjectOpenListener {
    void onProjectOpen(Project project);
  }

  private static volatile ProjectManager INSTANCE = null;

  public static synchronized ProjectManager getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new ProjectManager();
    }
    return INSTANCE;
  }

  public static String[] getTaskList() {
        return indexFiles.keySet().toArray(new String[0]);
    }

  private final List<OnProjectOpenListener> mProjectOpenListeners = new ArrayList<>();
  private volatile Project mCurrentProject;

  private ProjectManager() {}

  public void addOnProjectOpenListener(OnProjectOpenListener listener) {
    if (!CompletionEngine.isIndexing() && mCurrentProject != null) {
      listener.onProjectOpen(mCurrentProject);
    }
    mProjectOpenListeners.add(listener);
  }

  public void removeOnProjectOpenListener(OnProjectOpenListener listener) {
    mProjectOpenListeners.remove(listener);
  }

  public void openProject(
      Project project, boolean downloadLibs, TaskListener listener, ILogger logger) {
    ProgressManager.getInstance()
        .runNonCancelableAsync(() -> doOpenProject(project, downloadLibs, listener, logger));
  }

  private void doOpenProject(
      Project project, boolean downloadLibs, TaskListener mListener, ILogger logger) {
    mCurrentProject = project;
    IProjectManager.getInstance().currentProject = project;
    Module module = mCurrentProject.getMainModule();



    now = Instant.now();
    boolean shouldReturn = false;
    File gradleFile = module.getGradleFile();

    // Index the project after downloading dependencies so it will get added to classpath
    try {
      List<String> plugins = new ArrayList<>();
      List<String> unsupported_plugins = new ArrayList<>();
      if(module.getPlugins()!=null){
      for (String plugin : module.getPlugins()) {
        if(plugin!=null){
        if (plugin.equals("java-library")
            || plugin.equals("com.android.library")
            || plugin.equals("com.android.application")
            || plugin.equals("kotlin")
            || plugin.equals("application")
            || plugin.equals("kotlin-android")) {
          plugins.add(plugin);
        } else {
          unsupported_plugins.add(plugin);
        }
        } 
      } 
      }

      String pluginType = plugins.toString();

      if (gradleFile.exists()) {
        logger.debug("> Task :" + module.getModuleName() + ":" + "checkingPlugins");
        if (plugins.isEmpty()) {
          logger.error("No plugins applied");
          shouldReturn = true;
        } else {
          logger.debug("NOTE: " + "Plugins applied: " + plugins.toString());
          if (unsupported_plugins.isEmpty()) {
          } else {
            logger.debug(
                "NOTE: "
                    + "Unsupported plugins: "
                    + unsupported_plugins.toString()
                    + " will not be used");
          }
        }
      }
      mCurrentProject.open();
    } catch (IOException exception) {
      logger.warning("Failed to open project: " + exception.getMessage());
      shouldReturn = true;
    }
    mProjectOpenListeners.forEach(it -> it.onProjectOpen(mCurrentProject));

    if (shouldReturn) {
      mListener.onComplete(project, false, "Failed to open project.");
      return;
    }

    try {
      mCurrentProject.setIndexing(true);
      mCurrentProject.index();
    } catch (IOException exception) {
      logger.warning("Failed to open project: " + exception.getMessage());
    }

    Consumer<File> modifiedEventConsumer = file -> {
            // we only want xml files
            if (!ProjectUtils.isResourceXMLFile(file)) {
                return;
            }
            // this will cause an update to repository, causing reparse to the affected file
            mCurrentProject.getEventManager().dispatchEvent(
                    new XmlResourceChangeEvent(file, null)
            );
        };
        mCurrentProject.getEventManager().subscribeEvent(FileDeletedEvent.class, (event, u) -> {
            modifiedEventConsumer.accept(event.getDeletedFile());

            mCurrentProject.getEventManager().dispatchEvent(new XmlReparsedEvent(event.getDeletedFile()));
        });
        // listen for newly created files and notify the resources repository
        mCurrentProject.getEventManager().subscribeEvent(FileCreatedEvent.class, (event, u) -> {
            modifiedEventConsumer.accept(event.getFile());
        });
        mCurrentProject.getEventManager().subscribeEvent(XmlReparsedEvent.class,
                (event, unsubscribe) -> DebouncerStore.DEFAULT.registerOrGetDebouncer("ResourceInjector").debounce(300, () -> ProgressManager.getInstance().runNonCancelableAsync(() -> {
                    File file = event.getFile();
                    Module module2;
                    if (file == null) {
                       // module2 = mCurrentProject.getModuleByName(":app");
                        module2 = mCurrentProject.getMainModule();
                    } else {
                        module2 = mCurrentProject.getResModule(file);
                    }
                    if (module2 instanceof AndroidModule && indexFiles.containsKey(RES)) {
                        try {
                            InjectResourcesTask.inject(mCurrentProject, (AndroidModule) module2);
                            //InjectResourcesTask.inject(project, (AndroidModule) module);
                            InjectViewBindingTask.inject(mCurrentProject, (AndroidModule) module2);
                        } catch (IOException e) {
                            IdeLog.getLogger().severe(e.getMessage());
                        }
                    }
                })));

     // the following will extract the jar files if it does not exist
       BuildModule.getAndroidJar();
       BuildModule.getLambdaStubs();

    JavaModule javaModule = (JavaModule) module;
    if (gradleFile.exists() && indexFiles.containsKey(DOWNLOAD)) {
      if (module instanceof JavaModule) {
        try {
          downloadLibraries(javaModule, mListener, logger);
        } catch (IOException e) {
          logger.error(e.getMessage());
        }
      }
    }

    AndroidModule androidModule = (AndroidModule) module;

    File res = androidModule.getAndroidResourcesDirectory();
    if (res.exists()) {

      if (module instanceof AndroidModule && indexFiles.containsKey(RES)) {
        mListener.onTaskStarted("Generating resource files.");
        ManifestMergeTask manifestMergeTask =
            new ManifestMergeTask(project, (AndroidModule) module, logger);
        IncrementalAapt2Task task =
            new IncrementalAapt2Task(project, (AndroidModule) module, logger, false);
        try {
          logger.debug("> Task :" + module.getModuleName() + ":" + "generatingResources");
          String packageName = getApplicationId(((AndroidModule) module));
          if (packageName != null) {
            manifestMergeTask.prepare(BuildType.DEBUG);
            manifestMergeTask.run();
          } else {
            throw new IOException(
                "Unable to find namespace or applicationId in "
                    + module.getModuleName()
                    + "/build.gradle file");
          }
          task.prepare(BuildType.DEBUG);
          task.run();
        } catch (IOException | CompilationFailedException e) {
        }
      }

      if (res.exists()) {
        if (module instanceof JavaModule && indexFiles.containsKey(XML)) {
          if (module instanceof AndroidModule) {
            mListener.onTaskStarted("Indexing XML files.");

            //new 
            ResourceRepositoryManager.getProjectResources((AndroidModule)module);

//            XmlIndexProvider index = CompilerService.getInstance().getIndex(XmlIndexProvider.KEY);
//            index.clear();

//            XmlRepository xmlRepository = index.get(project, module);
            LayoutRepo repo = LayoutRepo.get((AndroidModule)module);
            try {
              String packageName = getApplicationId(((AndroidModule) module));
              if (packageName != null) {
                logger.debug(
                    "> Task :" + module.getModuleName() + ":" + "indexingResources");
//                xmlRepository.initialize((AndroidModule) module);
                repo.initialize((AndroidModule) module,true);

              }
            } catch (IOException e) {
              String message =
                  "Unable to initialize resource repository. "
                      + "Resource code completion might be incomplete or unavailable. \n"
                      + "Reason: "
                      + e.getMessage();
              LOG.warning(message);
            }
          }
        }

        mListener.onTaskStarted("Indexing");
        try {
          if(indexFiles.containsKey(JAVA)){
              if (res.exists()&&indexFiles.containsKey(INJECT_RES)) {
                if (module instanceof AndroidModule) {
                  String packageName = getApplicationId(((AndroidModule) module));
                  if (packageName != null) {
                    InjectResourcesTask.inject(project, (AndroidModule) module);
                    InjectViewBindingTask.inject(project, (AndroidModule) module);
//                    mCurrentProject.getEventManager().dispatchEvent(new XmlReparsedEvent(null)); 
                    logger.debug(
                        "> Task :" + module.getModuleName() + ":" + "injectingResources");
                  }
                }
              }
       //       indexModule(module);
             CompilationInfo info = CompilationInfo.get(module,true);
//             KotlinEnvironment kotlinEnvironment = KotlinEnvironment.Companion.get(module,true); 
          }
        } catch (Throwable e) {
          String message = "Failure indexing project.\n" + Throwables.getStackTraceAsString(e);
          mListener.onComplete(project, false, message);
        }
        }
    }

   // mProjectOpenListeners.forEach(it -> it.onProjectOpen(mCurrentProject));
    var projectInitializedEvent = new ProjectInitializedEvent();
    projectInitializedEvent.put(Module.class,module);
    ((DefaultLanguageServerRegistry)ILanguageServerRegistry.getDefault()).onProjectInitialized(projectInitializedEvent);
    mCurrentProject.setIndexing(false);
    mListener.onComplete(project, true, "Index successful");

    long milliseconds = Duration.between(now, Instant.now()).toMillis();
    long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
    if (seconds > 60) {
      long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds);
      logger.debug("TIME TOOK " + minutes + "m");
    } else {
      logger.debug("TIME TOOK " + seconds + "s");
    }
  }

  private synchronized void indexModule(Module module) throws IOException {
      //  module.open();
      //  module.index();
         if(!(module instanceof JavaModule))return;
        JavaModule javaModule = (JavaModule) module;
        for (File value : javaModule.getJavaFiles().values()) {
            CompilationInfo info = CompilationInfo.get(module.getProject(), value,true);
            if (info == null) {
                continue;
            }
            info.updateImmediately(new SimpleJavaFileObject(value.toURI(),
                    JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    Parser parser = Parser.parseFile(module.getProject(), value.toPath());
                    // During indexing, statements inside methods are not needed so
                    // it is stripped to speed up the index process
                    return new PruneMethodBodies(info.impl.getJavacTask()).scan(parser.root, 0L);
                }
            });
        }

    //    KotlinEnvironment kotlinEnvironment = KotlinEnvironment.Companion.get(module,true);
    }

  private void downloadLibraries(JavaModule project, TaskListener listener, ILogger logger)
      throws IOException {
    DependencyManager manager =
        new DependencyManager(
            project, ApplicationLoader.applicationContext.getExternalFilesDir("cache"));
    manager.resolve(project, listener, logger);
  }

  public void closeProject(@NonNull Project project) {
    if (project.equals(mCurrentProject)) {
      mCurrentProject = null;
      IProjectManager.getInstance().currentProject = null;
    }
  }

  public synchronized Project getCurrentProject() {
    return mCurrentProject;
  }

  public static File createFile(File directory, String name, CodeTemplate template)
      throws IOException {
    if (!directory.isDirectory()) {
      return null;
    }

    String code = template.get().replace(CodeTemplate.CLASS_NAME, name);

    File classFile = new File(directory, name + template.getExtension());
    if (classFile.exists()) {
      return null;
    }
    if (!classFile.createNewFile()) {
      return null;
    }

    FileUtils.writeStringToFile(classFile, code, Charsets.UTF_8);
    return classFile;
  }

  @Nullable
  public static File createClass(File directory, String className, CodeTemplate template)
      throws IOException {
    if (!directory.isDirectory()) {
      return null;
    }

    String packageName = ProjectUtils.getPackageName(directory);
    if (packageName == null) {
      return null;
    }

    String code =
        template
            .get()
            .replace(CodeTemplate.PACKAGE_NAME, packageName)
            .replace(CodeTemplate.CLASS_NAME, className);

    File classFile = new File(directory, className + template.getExtension());
    if (classFile.exists()) {
      return null;
    }
    if (!classFile.createNewFile()) {
      return null;
    }

    FileUtils.writeStringToFile(classFile, code, Charsets.UTF_8);
    return classFile;
  }

  private String getApplicationId(AndroidModule module) {
    try {
      String packageName = module.getNameSpace();
      String content = parseString(module.getGradleFile());

      if (content != null) {
        if (content.contains("namespace") && !content.contains("applicationId")) {
          throw new IOException(
              "Unable to find applicationId in "
                  + module.getModuleName()
                  + "/build.gradle file");

        } else if (content.contains("applicationId") && content.contains("namespace")) {
          return packageName;
        } else if (content.contains("applicationId") && !content.contains("namespace")) {
          packageName = module.getApplicationId();
        } else {
          throw new IOException(
              "Unable to find namespace or applicationId in "
                  + module.getModuleName()
                  + "/build.gradle file");
        }
      } else {
        throw new IOException(
            "Unable to read " + module.getModuleName() + "/build.gradle file");
      }
    } catch (IOException e) {

    }
    return null;
  }

  private String parseString(File gradle) {
    if (gradle != null && gradle.exists()) {
      try {
        String readString = FileUtils.readFileToString(gradle, Charset.defaultCharset());
        if (readString != null && !readString.isEmpty()) {
          return readString;
        }
      } catch (IOException e) {
        // handle the exception here, if needed
      }
    }
    return null;
  }
}