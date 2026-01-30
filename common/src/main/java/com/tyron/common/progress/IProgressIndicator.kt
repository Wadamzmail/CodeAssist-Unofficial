package com.tyron.common.progress

import androidx.annotation.FloatRange

/**
 * A progress indicator reports progress of a specific task.
 *
 * @author Akash Yadav
 */
interface IProgressIndicator {

  /**
   * Called when the task begins its execution.
   */
  fun onStart()

  /**
   * Called each time the task progresses.
   *
   * @param progress The progress of the task. Must be a value between 0 (start) and 1 (finish).
   */
  fun onProgress(@FloatRange(from = 0.0, to = 1.0) progress: Float)

  /**
   * Called when the task finishes its execution.
   */
  fun onFinish()
}