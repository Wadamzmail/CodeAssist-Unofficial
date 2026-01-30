package com.tyron.completion.model

import com.tyron.common.progress.ICancelChecker

/**
 * Parameters for requests which support cancellation.
 *
 * @author Akash Yadav
 */
interface CancellableRequestParams {

  /**
   * The cancel checker.
   */
  val cancelChecker: ICancelChecker
}