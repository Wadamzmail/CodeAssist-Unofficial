package com.tyron.completion.java.action

import com.tyron.actions.ActionPlaces
import com.tyron.actions.AnAction
import com.tyron.actions.AnActionEvent
import com.tyron.actions.CommonDataKeys
import com.tyron.actions.Presentation
import com.tyron.completion.java.R
import com.tyron.code.ui.editor.IDEEditor
import org.slf4j.LoggerFactory
import com.tyron.completion.java.parse.CompilationInfo
import com.tyron.completion.java.provider.DefaultJavacUtilitiesProvider

class FieldBasedAction : AnAction() {

  companion object {

    private val log = LoggerFactory.getLogger(FieldBasedAction::class.java)
  } 
  
  override fun update(event : AnActionEvent){
     var presentation = event.presentation
     presentation.setVisible(false)  
     if (!ActionPlaces.EDITOR.equals(event.place))return
     
     val editor = event.getData(CommonDataKeys.EDITOR)?: return
     val file = event.getRequiredData(CommonDataKeys.FILE)?: return 
   
     val compilationInfo = event.getData(CompilationInfo.COMPILATION_INFO_KEY)?: return
     presentation.setVisible(true)
     //presentation.setText(event.dataContext.getString(R.string.menu_generators_generate_missing_constructor_title))
   }
  
  override fun actionPerformed(event : AnActionEvent){
     val editor = event.getData(CommonDataKeys.EDITOR) as? IDEEditor ?: return 
     val file = event.getRequiredData(CommonDataKeys.FILE)?: return
     
     val compilationInfo = event.getData(CompilationInfo.COMPILATION_INFO_KEY)?: return
     val unit = compilationInfo.getCompilationUnit(file.toURI())?: return
     val javacTask = compilationInfo.impl.javacTask
     val task = DefaultJavacUtilitiesProvider(javacTask, unit, editor.project) 
  }   

}