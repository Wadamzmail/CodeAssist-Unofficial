package io.github.rosemoe.sora2.text;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.tm4e.core.internal.theme.raw.RawTheme;
import org.eclipse.tm4e.core.internal.theme.raw.RawThemeReader;
import org.eclipse.tm4e.core.internal.theme.raw.IRawTheme;
import org.eclipse.tm4e.core.internal.theme.raw.IRawThemeSetting;
import org.eclipse.tm4e.core.registry.IThemeSource;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;


import java.lang.reflect.Method;
import java.util.Collection;

import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.text.ContentLine;
import io.github.rosemoe.sora.text.ICUUtils;
import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
 
public class EditorUtil {

    public static final String KEY_BACKGROUND = "background";
    public static final String KEY_BLOCK_LINE = "blockLineColor";
    public static final String KEY_CURRENT_BLOCK_LINE = "currentBlockLineColor";
    public static final String KEY_COMPLETION_WINDOW_BACKGROUND = "completionWindowBackground";
    public static final String KEY_COMPLETION_WINDOW_STROKE = "completionWindowStroke";

    @NonNull
    public static TextMateColorScheme createTheme(/*ThemeModel themeModel*/) throws Exception {
        TextMateColorScheme scheme = TextMateColorScheme.create(/*themeModel*/ThemeRegistry.getInstance());
        scheme.setTheme(/*themeModel*/ThemeRegistry.getInstance().getCurrentThemeModel());
       /*try{
        IRawTheme rawTheme = scheme.getRawTheme();
        Collection<IRawThemeSetting> settings = rawTheme.getSettings();
        if (settings != null && settings.size() >= 1) {
            RawTheme setting = (RawTheme) settings.iterator().next();
            setting = (RawTheme) setting.getSetting();

            Object blockLine = setting.get(KEY_BLOCK_LINE);
            if (blockLine != null) {
                scheme.setColor(EditorColorScheme.BLOCK_LINE, getColor(blockLine));
            }

            Object currBlockLine = setting.get(KEY_CURRENT_BLOCK_LINE);
            if (currBlockLine == null) {
                currBlockLine = blockLine;
            }
            if (currBlockLine != null) {
                scheme.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, getColor(currBlockLine));
            }

            Object completionWindowBackground = setting.get(KEY_COMPLETION_WINDOW_BACKGROUND);
            if (completionWindowBackground == null) {
                completionWindowBackground = setting.get(KEY_BACKGROUND);
            }
            scheme.setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND,
                            getColor(completionWindowBackground));

            Object completionStroke = setting.get(KEY_COMPLETION_WINDOW_STROKE);
            scheme.setColor(EditorColorScheme.COMPLETION_WND_CORNER,
                            getColor(completionStroke, Color.TRANSPARENT));
        }
       }catch(Exception e){}*/
        return scheme;
    }


    private static int getColor(@Nullable Object color) {
        return getColor(color, Color.WHITE);
    }

    private static int getColor(@Nullable Object color, @ColorInt int def) {
        if (!(color instanceof String)) {
            return def;
        }
        try {
            return Color.parseColor((String) color);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public static boolean isDarkMode(Context context) {
        int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static TextMateColorScheme getDefaultColorScheme(Context context) {
        try {
            boolean darkMode = isDarkMode(context);
            if (darkMode) {
                return getDefaultColorScheme(context, false);
            } else {
                return getDefaultColorScheme(context, true);
            }
        } catch (Exception e) {
            // should not happen, the bundled theme should always work.
            throw new Error(e);
        }
    }

    public static TextMateColorScheme getDefaultColorScheme(Context context, boolean light) {
        try {
            /*AssetManager assets = context.getAssets();
            IRawTheme rawTheme;
            if (light) {
                rawTheme = RawThemeReader.readThemeSync("QuietLight.tmTheme", assets.open(
                        "textmate/QuietLight.tmTheme"));
            } else {
                rawTheme = RawThemeReader.readThemeSync("darcula.json",
                                                     assets.open("textmate/darcula.json"));
            }*/
           
             String path = "";
            if(light){
              path = "textmate/QuietLight.tmTheme";
            }else{
               path =  "textmate/darcula.json";
            }
        /*  IThemeSource themeSource =   IThemeSource.fromInputStream(
                        FileProviderRegistry.getInstance().tryGetInputStream(path), path, null
                    );
           // return createTheme(rawTheme);
         ThemeModel themeModel = new ThemeModel(themeSource);
         themeModel.setDark(!light);
         themeModel.load();*/
        
         ThemeRegistry themeRegistry = ThemeRegistry.getInstance();
String name = path; //light?"quietlight":"darcula"; // name of theme
String themeAssetsPath =path;
ThemeModel model = new ThemeModel(
        IThemeSource.fromInputStream(
            FileProviderRegistry.getInstance().tryGetInputStream(themeAssetsPath), themeAssetsPath, null
        ), 
        name
    );
// If the theme is dark
 model.setDark(!light); 
         model.load();
themeRegistry.loadTheme(model);
         
            return createTheme(/*themeModel*/);
        } catch (Exception e) {
            // should not happen, the bundled theme should always work.
            throw new Error(e);
        }
    }

    public static int getFormatIndent(Language language, String line) {
        Class<? extends Language> aClass = language.getClass();
        try {
            Method getIndentAdvance = aClass.getDeclaredMethod("getFormatIndent", String.class);
            Object indent = getIndentAdvance.invoke(language, line);
            if (indent instanceof Integer) {
                return (int) indent;
            }
        } catch (Throwable e) {
            // ignored
        }
        return 0;
    }

    public static boolean isWhitespace(CharSequence charSequence) {
        for (int i = 0; i < charSequence.length(); i++) {
            char c = charSequence.charAt(i);
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }
    public static void selectWord(CodeEditor editor, int line, int column) {
        // Find word edges
        int startLine = line, endLine = line;
        ContentLine lineObj = editor.getText().getLine(line);
        long edges = ICUUtils.getWordRange(lineObj, column, false);
        int startColumn = IntPair.getFirst(edges);
        int endColumn = IntPair.getSecond(edges);
        if (startColumn == endColumn) {
            if (startColumn > 0) {
                startColumn--;
            } else if (endColumn < lineObj.length()) {
                endColumn++;
            } else {
                if (line > 0) {
                    int lastColumn = editor.getText().getColumnCount(line - 1);
                    startLine = line - 1;
                    startColumn = lastColumn;
                } else if (line < editor.getLineCount() - 1) {
                    endLine = line + 1;
                    endColumn = 0;
                }
            }
        }
        editor.setSelectionRegion(startLine, startColumn, endLine, endColumn, SelectionChangeEvent.CAUSE_LONG_PRESS);
    }
} 
