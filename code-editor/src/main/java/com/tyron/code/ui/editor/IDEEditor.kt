package com.tyron.code.ui.editor

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.SizeUtils
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import com.tyron.completion.model.signatures.SignatureHelp
import com.tyron.completion.model.signatures.SignatureHelpParams
import com.tyron.completion.model.signatures.SignatureHelpLanguage
import com.tyron.completion.model.location.Position
import com.tyron.completion.model.location.Range
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import com.tyron.common.tasks.JobCancelChecker
import com.tyron.common.tasks.cancelIfActive
import java.io.File
import com.tyron.common.progress.ICancelChecker
import com.tyron.completion.util.CancelChecker
import com.tyron.code.ui.editor.snippets.AbstractSnippetVariableResolver
import com.tyron.code.ui.editor.snippets.FileVariableResolver
import com.tyron.code.ui.editor.snippets.WorkspaceVariableResolver
import com.tyron.editor.Editor 

/*
*
* @author Wadamzmail
*/

abstract class IDEEditor @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  defStyleRes: Int = 0, 
  private val editorFeatures: EditorFeatures = EditorFeatures()
) : CodeEditor(context, attrs, defStyleAttr, defStyleRes), IEditor by editorFeatures, Editor{

  protected var _signatureHelpWindow: SignatureHelpWindow? = null
  protected var _diagnosticWindow: DiagnosticWindow? = null
  private var sigHelpCancelChecker: ICancelChecker? = null
  
  @JvmField 
  var mCurrentFile: File? = null
  
   /**
   * The [CoroutineScope] for the editor.
   *
   * All the jobs in this scope are cancelled when the editor is released.
   */
  val editorScope = CoroutineScope(Dispatchers.Default + CoroutineName("IDEEditor"))
       
  private val selectionChangeHandler = Handler(Looper.getMainLooper())
  private var selectionChangeRunner: Runnable? = Runnable {
    val cursor = this.cursor ?: return@Runnable

    if (cursor.isSelected || _signatureHelpWindow?.isShowing == true) {
      return@Runnable
    }
    
    onSelectionChange()

  }
  
  protected abstract fun onSelectionChange()
  
  companion object {
     private const val SELECTION_CHANGE_DELAY = 500L
     internal val log = LoggerFactory.getLogger(IDEEditor::class.java)
  } 
  init {
    run {
      editorFeatures.editor = this
      initEditor()
    }
  }
  
  val signatureHelpWindow: SignatureHelpWindow
    get() {
      return _signatureHelpWindow ?: SignatureHelpWindow(this).also { _signatureHelpWindow = it }
    }

  /**
   * The diagnostic window for the editor.
   */
  val diagnosticWindow: DiagnosticWindow
    get() {
      return _diagnosticWindow ?: DiagnosticWindow(this).also { _diagnosticWindow = it }
    }
    
  fun signatureHelp() {
    if (isReleased) {
      return
    }
    val file = this.file ?: return
    
    val language = this.editorLanguage as? SignatureHelpLanguage ?: return
    
    sigHelpCancelChecker?.also { it.cancel() }

    val cancelChecker = JobCancelChecker().also {
      this.sigHelpCancelChecker = it
    }

    editorScope.launch(Dispatchers.Default) {
      cancelChecker.job = coroutineContext[Job]

      val help = safeGet("signature help request") {
        val params = SignatureHelpParams(file.toPath(), cursorLSPPosition, cancelChecker)
        language.signatureHelp(params)
      }

      withContext(Dispatchers.Main) {
        showSignatureHelp(help)
      }
    }.logError("signature help request")
  }

  fun showSignatureHelp(help: SignatureHelp?) {
    if (isReleased) {
      return
    }
    signatureHelpWindow.setupAndDisplay(help)
  }  
  
  
  fun ensureWindowsDismissed() {
    if (_diagnosticWindow?.isShowing == true) {
      _diagnosticWindow?.dismiss()
    }

    if (_signatureHelpWindow?.isShowing == true) {
      _signatureHelpWindow?.dismiss()
    }
  }
  
  override fun release() {
    ensureWindowsDismissed()

    if (isReleased) {
      return
    }

    super.release()
    
    snippetController.apply {
      (fileVariableResolver as? AbstractSnippetVariableResolver?)?.close()
      (workspaceVariableResolver as? AbstractSnippetVariableResolver?)?.close()

      fileVariableResolver = null
      workspaceVariableResolver = null
    }

    _signatureHelpWindow = null
    _diagnosticWindow = null
    editorFeatures.editor = null

    selectionChangeRunner?.also { selectionChangeHandler.removeCallbacks(it) }
    selectionChangeRunner = null

    if (editorScope.isActive) {
      editorScope.cancelIfActive("Editor is releasing resources.")
    }
  }
  
  /**
   * Initialize the editor.
   */
  protected open fun initEditor() {
  
     DiagnosticWindow(this).also { _diagnosticWindow = it }
     SignatureHelpWindow(this).also { _signatureHelpWindow = it }
     
     snippetController.apply {
        fileVariableResolver = FileVariableResolver(this@IDEEditor)
        workspaceVariableResolver = WorkspaceVariableResolver()
      }

    subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
      if (isReleased) {
        return@subscribeEvent
      }

      editorScope.launch {
        checkForSignatureHelp(event)
      }
    }

    subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
      if (isReleased) {
        return@subscribeEvent
      }

      if (_diagnosticWindow?.isShowing == true) {
        _diagnosticWindow?.dismiss()
      }

      selectionChangeRunner?.also {
        selectionChangeHandler.removeCallbacks(it)
        selectionChangeHandler.postDelayed(it, SELECTION_CHANGE_DELAY)
      }
    }
  }
  
  
  /**
   * Checks if the content change event should trigger signature help. Signature help trigger
   * characters are :
   *
   *
   *  * `'('` (parentheses)
   *  * `','` (comma)
   *
   *
   * @param event The content change event.
   */
  private fun checkForSignatureHelp(event: ContentChangeEvent) {
    if (isReleased) {
      return
    }
    val changeLength = event.changedText.length
    if (event.action != ContentChangeEvent.ACTION_INSERT || changeLength < 1 || changeLength > 2) {
      // change length will be 1 if ',' is inserted
      // changeLength will be 2 as '(' and ')' are inserted at the same time
      return
    }

    val ch = event.changedText[0]
    if (ch == '(' || ch == ',') {
      signatureHelp()
    }
  }

  private inline fun <T> safeGet(name: String, action: () -> T): T? {
    return try {
      action()
    } catch (err: Throwable) {
      logError(err, name)
      null
    }
  }

  private fun Job.logError(action: String): Job = apply {
    invokeOnCompletion { err -> logError(err, action) }
  }

  private fun logError(err: Throwable?, action: String) {
    err ?: return
    if (CancelChecker.isCancelled(err)) {
      log.warn("{} has been cancelled", action)
    } else {
      log.error("{} failed", action)
    }
  }
  
  override fun setSelectionAround(line: Int, column: Int) {
    editorFeatures.setSelectionAround(line, column)
  }

} 