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
import com.tyron.completion.java.parse.CompilationInfo
import com.sun.source.tree.LineMap
import javax.tools.Diagnostic
import javax.tools.JavaFileObject
import com.tyron.completion.model.Position
import com.tyron.completion.model.Range
import com.tyron.builder.model.DiagnosticWrapper

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
     val task = DefaultJavacUtilitiesProvider(javacTask, unit, editor.project)
     val needsConstructor =
        CodeActionUtils.findClassNeedingConstructor(task, getDiagnosticRange(diagnostic,unit.lineMap)) ?: return
     val rewrite = GenerateRecordConstructor(needsConstructor)?: return 
     RewriteUtil.performRewrite(
         editor,
         file,
         task,
         rewrite)
   }
   
   private fun getDiagnosticRange(
    diagnostic: DiagnosticWrapper ,
    lines: LineMap
  ): Range {
    val start = getPosition(diagnostic.startPosition, lines)
    val end = getPosition(diagnostic.endPosition, lines)
    return Range(start, end)
  }

  private fun getPosition(position: Long, lines: LineMap): Position {
    // decrement the numbers
    // to convert 1-based indexes to 0-based
    val line = (lines.getLineNumber(position) - 1).toInt()
    val column = (lines.getColumnNumber(position) - 1).toInt()
    return Position(line, column)
  }

}