package com.tyron.common.tasks

import com.tyron.common.progress.ICancelChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.InterruptedIOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * An [ICancelChecker] which when cancelled, cancels the corresponding [Job].
 */
class JobCancelChecker @JvmOverloads constructor(
  var job: Job? = null
) : ICancelChecker.Default() {

  override fun cancel() {
    job?.cancel("Cancelled by user")
    job = null
    super.cancel()
  }
}

/**
 * Calls [CoroutineScope.cancel] only if a job is active in the scope.
 *
 * @param message Optional message describing the cause of the cancellation.
 * @param cause Optional cause of the cancellation.
 * @see cancelIfActive
 */
fun CoroutineScope.cancelIfActive(message: String, cause: Throwable? = null) =
  cancelIfActive(CancellationException(message, cause))

/**
 * Calls [CoroutineScope.cancel] only if a job is active in the scope.
 *
 * @param exception Optional cause of the cancellation.
 */
fun CoroutineScope.cancelIfActive(exception: CancellationException? = null) {
  val job = coroutineContext[Job]
  job?.cancel(exception)
}

/**
 * Runs the given [action] and re-throws the [Throwable] if this [Throwable] is thrown for
 * cancellation or interruption purposes.
 *
 * @param action The action to run if this [Throwable] was cancelled or interrupted.
 */
inline fun Throwable.ifCancelledOrInterrupted(suppress: Boolean = false, action: () -> Unit) {
  when (this) {
    is CancellationException,
    is InterruptedException,
    is InterruptedIOException -> {
      action()
      if (!suppress) {
        throw this
      }
    }
  }
}