package com.tyron.completion.java.action.common

import com.tyron.actions.ActionPlaces
import com.tyron.actions.AnAction
import com.tyron.actions.AnActionEvent
import com.tyron.actions.CommonDataKeys
import com.tyron.actions.Presentation
import com.tyron.completion.java.R
import com.tyron.editor.Editor
import io.github.rosemoe.sora.widget.CodeEditor 
import com.tyron.code.ui.editor.IDEEditor
import com.google.googlejavaformat.java.FormatterException
import com.google.googlejavaformat.java.ImportOrderer
import org.slf4j.LoggerFactory
import com.google.googlejavaformat.java.JavaFormatterOptions.Style.GOOGLE

class OrganizeImportsAction : AnAction() {
 
 companion object {
   const val ID: String = "javaOrganizeImportsAction"
   private val log = LoggerFactory.getLogger(OrganizeImportsAction::class.java)
 } 
   override fun update(event : AnActionEvent){
     var presentation = event.presentation
     presentation.setVisible(false)
     
     if (!ActionPlaces.EDITOR.equals(event.place))return
     
     val editor = event.getData(CommonDataKeys.EDITOR)?: return
     presentation.setVisible(true)
     presentation.setText(event.dataContext.getString(R.string.menu_common_organize_imports_title))
   }
   
   override fun actionPerformed(event : AnActionEvent){
     val editor = event.getData(CommonDataKeys.EDITOR) as? IDEEditor ?: return 
     val text = editor.text
     val cursor = editor.cursor

     try{
      val result = ImportOrderer.reorderImports(text.toString(), GOOGLE)
      if (result.isNotEmpty()) {
      
        editor.text.apply {
          val endLine = getLine(lineCount - 1)
          replace(0, 0, lineCount - 1, endLine.length + endLine.lineSeparator.length, result)
        }

        editor?.also {
          it.setSelectionAround(cursor.left())
          editor.ensureSelectionVisible()
        }
      }
     } catch (e: FormatterException) {
      log.error("Failed to reorder imports", e)
     }
   }

}