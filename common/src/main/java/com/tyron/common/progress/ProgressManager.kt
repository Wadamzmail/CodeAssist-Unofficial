package com.tyron.common.progress

import com.tyron.common.progress.ICancelChecker.Default
import java.util.WeakHashMap
import java.util.concurrent.CancellationException

/**
 * @author Akash Yadav
 */
class ProgressManager private constructor() {

  private val threads = WeakHashMap<Thread, ICancelChecker>()

  companion object {

    val instance by lazy {
      ProgressManager()
    }

    @JvmStatic
    fun abortIfCancelled() {
      instance.abortIfCancelled()
    }
  }

  fun cancel(thread: Thread) {
    var checker = threads[thread]
    if (checker == null) {
      checker = Default()
    }
    checker.cancel()
    threads[thread] = checker
  }

  @JvmName("internalAbortIfCancelled")
  private fun abortIfCancelled() {
    val thisThread = Thread.currentThread()
    val checker = threads[thisThread]
    if (checker != null && checker.isCancelled()) {
      threads.remove(thisThread)
      throw CancellationException()
    }
  }
}