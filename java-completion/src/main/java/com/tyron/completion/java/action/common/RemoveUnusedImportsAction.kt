package com.tyron.completion.java.action.common

import com.tyron.actions.ActionPlaces
import com.tyron.actions.AnAction
import com.tyron.actions.AnActionEvent
import com.tyron.actions.CommonDataKeys
import com.tyron.actions.Presentation
import com.tyron.completion.java.R
import com.tyron.editor.Editor
import io.github.rosemoe.sora.widget.CodeEditor
import com.google.googlejavaformat.java.FormatterException
import com.google.googlejavaformat.java.RemoveUnusedImports
import org.slf4j.LoggerFactory
import com.tyron.code.ui.editor.IDEEditor 

class RemoveUnusedImportsAction : AnAction() {
   
   companion object {
    const val ID: String = "javaRemoveUnusedImportsAction" 
    private val log = LoggerFactory.getLogger(RemoveUnusedImportsAction::class.java)
  }
   override fun update(event : AnActionEvent){
     var presentation = event.presentation
     presentation.setVisible(false)
     
     if (!ActionPlaces.EDITOR.equals(event.place))return
     
     val editor = event.getData(CommonDataKeys.EDITOR)?: return
     presentation.setVisible(true)
     presentation.setText(event.dataContext.getString(R.string.menu_common_remove_unused_imports_title))
   }
   
   override fun actionPerformed(event : AnActionEvent){
     val editor = event.getData(CommonDataKeys.EDITOR) as? IDEEditor ?: return 
     val text = editor.text
     try{
       val output = RemoveUnusedImports.removeUnusedImports(text.toString())
       editor.setText(output)
     } catch (e: FormatterException) {
       log.error("Failed to remove unused imports", e)
     }
     
   }

}