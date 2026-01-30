package com.tyron.common.tasks

import android.app.ProgressDialog
import android.content.Context
import com.blankj.utilcode.util.ThreadUtils
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/** 
* @author Itsaky Akash
* modified by Wadamzmail
*/
object TaskExecutor {

  private val log = LoggerFactory.getLogger(TaskExecutor::class.java)

  @JvmOverloads
  @JvmStatic
  fun <R> executeAsync(
    callable: Callable<R>,
    callback: Callback<R>? = null
  ): CompletableFuture<R?> {
    return CompletableFuture.supplyAsync {
        try {
          return@supplyAsync callable.call()
        } catch (th: Throwable) {
          log.error("An error occurred while executing Callable in background thread.", th)
          return@supplyAsync null
        }
      }
      .whenComplete { result, _ -> ThreadUtils.runOnUiThread { callback?.complete(result) } }
  }

  @JvmOverloads
  @JvmStatic
  fun <R> executeAsyncProvideError(
    callable: Callable<R>,
    callback: CallbackWithError<R>? = null
  ): CompletableFuture<R?> {
    return CompletableFuture.supplyAsync {
        try {
          return@supplyAsync callable.call()
        } catch (th: Throwable) {
          log.error("An error occurred while executing Callable in background thread.", th)
          throw CompletionException(th)
        }
      }
      .whenComplete { result, throwable ->
        ThreadUtils.runOnUiThread { callback?.complete(result, throwable) }
      }
  }

  fun interface Callback<R> {
    fun complete(result: R?)
  }

  fun interface CallbackWithError<R> {
    fun complete(result: R?, error: Throwable?)
  }
}

fun <R : Any?> executeAsync(callable: () -> R?) {
  executeAsync(callable) {}
}

@JvmOverloads
@Suppress("DEPRECATION")
inline fun <T> Context.executeWithProgress(
  cancellable: Boolean = false,
  block: (ProgressDialog) -> T
): T {
  val dialog = ProgressDialog(this)
  dialog.setMessage("Please Wait")
  dialog.setCancelable(cancellable)
  dialog.show()
  return block(dialog)
}

fun <R : Any?> executeAsync(callable: () -> R?, callback: (R?) -> Unit): CompletableFuture<R?> =
  TaskExecutor.executeAsync({ callable() }) { callback(it) }

fun <R : Any?> executeAsyncProvideError(
  callable: () -> R?,
  callback: (R?, Throwable?) -> Unit
): CompletableFuture<R?> =
  TaskExecutor.executeAsyncProvideError(callable, callback)

fun runOnUiThread(action : () -> Unit) {
  ThreadUtils.runOnUiThread(action)
}
