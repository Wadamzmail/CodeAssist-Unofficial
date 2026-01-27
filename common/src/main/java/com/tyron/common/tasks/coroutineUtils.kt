/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

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