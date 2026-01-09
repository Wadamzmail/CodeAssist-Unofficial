package com.tyron.completion.xml.v2;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ViewFlipper;
import android.widget.ViewSwitcher;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.completion.index.CompilerService;
import com.tyron.completion.xml.model.AttributeInfo;
import com.tyron.completion.xml.model.DeclareStyleable;
import com.tyron.completion.xml.model.Format;
import com.tyron.completion.xml.util.StyleUtils;
import com.tyron.xml.completion.repository.ResourceRepository;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.jetbrains.kotlin.com.intellij.openapi.util.Key;
import com.tyron.completion.xml.BytecodeScanner;

public class LayoutRepo {

  private final Map<String, JavaClass> mJavaViewClasses = new TreeMap<>();

  private boolean mInitialized = false;
  
  private static final Key<LayoutRepo> LAYOUT_REPO_KEY = Key.create("layoutRepo_key");
  
  public LayoutRepo() {}

  public Map<String, JavaClass> getJavaViewClasses() {
    return mJavaViewClasses;
  }

  public void initialize(AndroidModule module) throws IOException {
    if (mInitialized) {
      return;
    }
    BytecodeScanner.scanBootstrapIfNeeded();

    for (File library : module.getLibraries()) {
      File parent = library.getParentFile();
      if (parent == null) {
        continue;
      }
      File classesFile = new File(parent, "classes.jar");
      if (classesFile.exists()) {
        try {
          BytecodeScanner.loadJar(classesFile);
        } catch (IOException ignored) {

        }
      }
    }

    for (File library : module.getLibraries()) {
      try {
        List<JavaClass> scan = BytecodeScanner.scan(library);
        for (JavaClass javaClass : scan) {
          StyleUtils.putStyles(javaClass);
          mJavaViewClasses.put(javaClass.getClassName(), javaClass);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    addFrameworkViews();

    Repository.clearCache();

    mInitialized = true;
  }

  private void addFrameworkViews() {
    addFrameworkView(View.class);
    addFrameworkView(ViewGroup.class);
    addFrameworkView(FrameLayout.class);
    addFrameworkView(RelativeLayout.class);
    addFrameworkView(LinearLayout.class);
    addFrameworkView(AbsoluteLayout.class);
    addFrameworkView(ListView.class);
    addFrameworkView(EditText.class);
    addFrameworkView(Button.class);
    addFrameworkView(TextView.class);
    addFrameworkView(ImageView.class);
    addFrameworkView(ImageButton.class);
    addFrameworkView(ImageSwitcher.class);
    addFrameworkView(ViewFlipper.class);
    addFrameworkView(ViewSwitcher.class);
    addFrameworkView(ScrollView.class);
    addFrameworkView(HorizontalScrollView.class);
    addFrameworkView(CompoundButton.class);
    addFrameworkView(ProgressBar.class);
    addFrameworkView(CheckBox.class);
  }

  private void addFrameworkView(Class<? extends View> viewClass) {
    org.apache.bcel.util.Repository repository = Repository.getRepository();
    try {
      JavaClass javaClass = repository.loadClass(viewClass);
      if (javaClass != null) {
        mJavaViewClasses.put(javaClass.getClassName(), javaClass);
      }
    } catch (ClassNotFoundException e) {
      // ignored
    }
  }
       
  public static LayoutRepo get( AndroidModule module) throws IOException{
     LayoutRepo repo = (LayoutRepo)module.getUserData(LAYOUT_REPO_KEY);
     if(repo!=null)return repo;
     repo = new LayoutRepo();
     repo.initialize(module);
     module.putUserData(LAYOUT_REPO_KEY,repo);
    return repo;
  }
}
