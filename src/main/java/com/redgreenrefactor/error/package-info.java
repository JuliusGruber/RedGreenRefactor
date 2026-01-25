/**
 * Error handling and recovery components for the TDD orchestrator.
 * <p>
 * This package provides:
 * <ul>
 *   <li>{@link com.redgreenrefactor.error.TDDErrorHandler} - Detection and classification of errors</li>
 *   <li>{@link com.redgreenrefactor.error.ErrorType} - Classification of error types</li>
 *   <li>{@link com.redgreenrefactor.error.RetryHandler} - Retry logic with exponential backoff</li>
 *   <li>{@link com.redgreenrefactor.error.RecoveryStrategy} - Recovery actions for different error types</li>
 * </ul>
 * <p>
 * Error handling flow:
 * <ol>
 *   <li>Error detected during phase execution</li>
 *   <li>{@link com.redgreenrefactor.error.TDDErrorHandler#classifyError} identifies the error type</li>
 *   <li>{@link com.redgreenrefactor.error.RecoveryStrategy#determineRecoveryAction} chooses recovery action</li>
 *   <li>{@link com.redgreenrefactor.error.RecoveryStrategy#executeRecovery} performs the recovery</li>
 *   <li>State is updated with error context for retry, or workflow is aborted</li>
 * </ol>
 */
package com.redgreenrefactor.error;
