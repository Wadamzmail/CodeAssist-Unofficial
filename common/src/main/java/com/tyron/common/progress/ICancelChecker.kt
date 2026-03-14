package com.tyron.common.progress

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Check whether a process is cancelled.
 *
 * @author Akash Yadav
 */
interface ICancelChecker {

  /**
   * Cancel this process.
   */
  fun cancel()

  /**
   * Check whether this process has been cancelled or not.
   *
   * @return Whether the process has been cancelled.
   */
  fun isCancelled(): Boolean

  /**
   * Throw [CancellationException] if this process has been cancelled.
   */
  @Throws(CancellationException::class)
  fun abortIfCancelled()

  open class Default(cancelled: Boolean = false) : ICancelChecker {

    private val cancelled = AtomicBoolean(cancelled)

    override fun cancel() {
      cancelled.set(true)
    }

    override fun isCancelled(): Boolean {
      return cancelled.get()
    }

    override fun abortIfCancelled() {
      if (isCancelled()) {
        throw CancellationException()
      }
    }
  }

  companion object {

    /**
     * A no-op cancel checker. The task is never cancelled.
     */
    @JvmField
    val NOOP = Default(false)

    /**
     * An already cancelled cancel checker.
     */
    @JvmField
    val CANCELLED = Default(true)
  }
}
