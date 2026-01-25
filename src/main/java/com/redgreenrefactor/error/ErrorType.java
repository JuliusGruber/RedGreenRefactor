package com.redgreenrefactor.error;

/**
 * Classifies the types of errors that can occur during TDD workflow execution.
 * Each error type maps to a specific recovery strategy.
 */
public enum ErrorType {

    /**
     * Test compilation failed - code does not compile.
     * Recovery: Retry with error context to fix syntax/type errors.
     */
    COMPILATION("Compilation error - code failed to compile"),

    /**
     * Tests failed during execution.
     * Recovery depends on phase:
     * - RED: Expected (test should fail)
     * - GREEN/REFACTOR: Unexpected, needs implementation fix
     */
    TEST_FAILURE("Test execution failed"),

    /**
     * Tests passed unexpectedly during RED phase.
     * The new test should fail but passed, indicating the test
     * is not properly asserting the missing functionality.
     * Recovery: Re-run Test Agent with instruction to add proper assertions.
     */
    UNEXPECTED_PASS("Tests passed unexpectedly in RED phase"),

    /**
     * Command or operation timed out.
     * Recovery: Retry with increased timeout or abort if persistent.
     */
    TIMEOUT("Operation timeout"),

    /**
     * API rate limit exceeded.
     * Recovery: Exponential backoff and retry.
     */
    RATE_LIMIT("API rate limit exceeded"),

    /**
     * Network or connection error.
     * Recovery: Retry with backoff.
     */
    NETWORK("Network connection error"),

    /**
     * Unknown or unclassified error.
     * Recovery: Log details and retry with general error context.
     */
    UNKNOWN("Unknown error occurred");

    private final String description;

    ErrorType(String description) {
        this.description = description;
    }

    /**
     * Returns a human-readable description of this error type.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns whether this error type is retriable.
     */
    public boolean isRetriable() {
        return switch (this) {
            case COMPILATION, TEST_FAILURE, UNEXPECTED_PASS, TIMEOUT, RATE_LIMIT, NETWORK, UNKNOWN -> true;
        };
    }

    /**
     * Returns whether this error type requires a rollback before retry.
     */
    public boolean requiresRollback() {
        return switch (this) {
            case TEST_FAILURE -> true;  // Implementation may have broken tests
            case COMPILATION, UNEXPECTED_PASS, TIMEOUT, RATE_LIMIT, NETWORK, UNKNOWN -> false;
        };
    }
}
