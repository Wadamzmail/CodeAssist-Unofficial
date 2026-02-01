package com.tyron.completion.java.action.generators

import com.tyron.actions.ActionPlaces
import com.tyron.actions.AnAction
import com.tyron.actions.AnActionEvent
import com.tyron.actions.CommonDataKeys
import com.tyron.actions.Presentation
import com.tyron.completion.java.R
import com.tyron.editor.Editor
import io.github.rosemoe.sora.widget.CodeEditor
import com.tyron.code.ui.editor.IDEEditor
import org.slf4j.LoggerFactory
import com.tyron.completion.java.rewrite.GenerateRecordConstructor
import com.tyron.completion.java.util.CodeActionUtils
import com.tyron.completion.java.provider.DefaultJavacUtilitiesProvider
import com.tyron.completion.util.RewriteUtil

class GenerateMissingConstructorAction : AnAction() {
     
   private val diagnosticCode = "compiler.err.var.not.initialized.in.default.constructor"
     
   companion object {
      const val ID: String = "GenerateMissingConstructorAction"
      private val log = LoggerFactory.getLogger(GenerateMissingConstructorAction::class.java)
   } 
   override fun update(event : AnActionEvent){
     var presentation = event.presentation
     presentation.setVisible(false)
     
     if (!ActionPlaces.EDITOR.equals(event.place))return
     
     val editor = event.getData(CommonDataKeys.EDITOR)?: return
     val file = event.getRequiredData(CommonDataKeys.FILE)?: return 
     val diagnostic = event.getData(CommonDataKeys.DIAGNOSTIC)?: return
     if (diagnosticCode != diagnostic.code) return
   
     val compilationInfo = event.getData(CompilationInfo.COMPILATION_INFO_KEY)?: return
     presentation.setVisible(true)
     presentation.setText(event.dataContext.getString(R.string.menu_generators_generate_missing_constructor_title))
   }
   
   override fun actionPerformed(event : AnActionEvent){
     val editor = event.getData(CommonDataKeys.EDITOR) as? IDEEditor ?: return 
     val file = event.getRequiredData(CommonDataKeys.FILE)?: return
     val diagnostic = event.getData(CommonDataKeys.DIAGNOSTIC)?: return
     if (diagnosticCode != diagnostic.code) return
     
     val compilationInfo = event.getData(CompilationInfo.COMPILATION_INFO_KEY)?: return
     val unit = compilationInfo.getCompilationUnit(file.toURI())?: return
     val javacTask = compilationInfo.impl.javacTask
     val needsConstructor =
        CodeActionUtils.findClassNeedingConstructor(javacTask, diagnostic.range) ?: return
     val rewrite = GenerateRecordConstructor(needsConstructor)?: return 
     RewriteUtil.performRewrite(
         editor,
         file,
         DefaultJavacUtilitiesProvider(javacTask, unit, editor.project),
         rewrite)
   }

}