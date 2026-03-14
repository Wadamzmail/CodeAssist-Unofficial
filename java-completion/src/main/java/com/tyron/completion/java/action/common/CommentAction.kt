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

class CommentAction : AnAction() {

  companion object {
      const val ID: String = "javaCommentAction"
  } 
   override fun update(event : AnActionEvent){
     var presentation = event.presentation
     presentation.setVisible(false)
     
     if (!ActionPlaces.EDITOR.equals(event.place))return
     
     val editor = event.getData(CommonDataKeys.EDITOR)?: return
     presentation.setVisible(true)
     presentation.setText(event.dataContext.getString(R.string.menu_common_comment_title))
   }
   
   override fun actionPerformed(event : AnActionEvent){
     val editor = event.getData(CommonDataKeys.EDITOR) as? IDEEditor ?: return 
     val text = editor.text
     val cursor = editor.cursor

     text.beginBatchEdit()
     for (line in cursor.leftLine..cursor.rightLine) {
        text.insert(line, 0, "//")
     } 
     text.endBatchEdit()
   }

}