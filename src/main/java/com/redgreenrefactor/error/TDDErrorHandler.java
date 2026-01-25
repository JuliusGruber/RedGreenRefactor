package com.redgreenrefactor.error;

import com.redgreenrefactor.model.Phase;
import com.redgreenrefactor.tool.ToolExecutionException;

import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Detects and classifies errors that occur during TDD workflow execution.
 * <p>
 * This handler analyzes tool outputs (especially from Bash commands) to identify
 * specific failure types that require different recovery strategies.
 */
public class TDDErrorHandler {

    // Test failure patterns for common frameworks
    private static final Pattern JUNIT_FAILURE_PATTERN = Pattern.compile(
            "(?i)(FAILURES!|Tests run:.*Failures:\\s*[1-9]|AssertionError|" +
                    "org\\.junit\\..*(Failure|Error)|" +
                    "expected:.*but was:|" +
                    "java\\.lang\\.AssertionError)"
    );

    private static final Pattern MAVEN_TEST_FAILURE_PATTERN = Pattern.compile(
            "(?i)(\\[ERROR\\].*Tests run:.*Failures:\\s*[1-9]|" +
                    "BUILD FAILURE.*tests failed|" +
                    "There are test failures)"
    );

    private static final Pattern GRADLE_TEST_FAILURE_PATTERN = Pattern.compile(
            "(?i)(FAILED|> Task :test FAILED|" +
                    "\\d+ tests? completed, \\d+ failed)"
    );

    private static final Pattern NPM_TEST_FAILURE_PATTERN = Pattern.compile(
            "(?i)(FAIL\\s+|npm ERR!.*test failed|" +
                    "\\d+ failing|" +
                    "AssertionError|" +
                    "expected .* to )"
    );

    private static final Pattern PYTEST_FAILURE_PATTERN = Pattern.compile(
            "(?i)(FAILED|\\d+ failed|" +
                    "AssertionError|" +
                    "=+ FAILURES =+)"
    );

    // Test pass patterns for detecting unexpected passes in RED phase
    private static final Pattern TEST_PASS_PATTERN = Pattern.compile(
            "(?is)(BUILD SUCCESS[\\s\\S]*Tests run:\\s*\\d+[\\s\\S]*Failures:\\s*0|" +
                    "Tests run:\\s*\\d+,\\s*Failures:\\s*0|" +
                    "OK \\(\\d+ tests?\\)|" +
                    "All \\d+ tests passed|" +
                    "✓|passed|" +
                    "0 failures|" +
                    "BUILD SUCCESSFUL)"
    );

    // Compilation error patterns
    private static final Pattern JAVA_COMPILATION_ERROR = Pattern.compile(
            "(?i)(\\[ERROR\\].*\\.java:\\[?\\d+|" +
                    "error: cannot find symbol|" +
                    "error: .*expected|" +
                    "COMPILATION ERROR|" +
                    "cannot be applied to|" +
                    "incompatible types)"
    );

    private static final Pattern TYPESCRIPT_COMPILATION_ERROR = Pattern.compile(
            "(?i)(error TS\\d+:|" +
                    "Cannot find name|" +
                    "Property .* does not exist|" +
                    "Type .* is not assignable)"
    );

    private static final Pattern PYTHON_SYNTAX_ERROR = Pattern.compile(
            "(?i)(SyntaxError:|" +
                    "IndentationError:|" +
                    "ModuleNotFoundError:|" +
                    "ImportError:)"
    );

    private static final Pattern GENERIC_COMPILATION_ERROR = Pattern.compile(
            "(?i)(compilation failed|" +
                    "syntax error|" +
                    "parse error|" +
                    "compile error)"
    );

    /**
     * Detects if the output indicates a test failure.
     *
     * @param bashOutput the output from running tests
     * @return true if test failures are detected
     */
    public boolean isTestFailure(String bashOutput) {
        if (bashOutput == null || bashOutput.isBlank()) {
            return false;
        }

        return JUNIT_FAILURE_PATTERN.matcher(bashOutput).find() ||
                MAVEN_TEST_FAILURE_PATTERN.matcher(bashOutput).find() ||
                GRADLE_TEST_FAILURE_PATTERN.matcher(bashOutput).find() ||
                NPM_TEST_FAILURE_PATTERN.matcher(bashOutput).find() ||
                PYTEST_FAILURE_PATTERN.matcher(bashOutput).find();
    }

    /**
     * Detects if tests passed unexpectedly during the RED phase.
     * In RED phase, the new test should FAIL. If all tests pass, it indicates
     * the test is not properly asserting the expected behavior.
     *
     * @param bashOutput the output from running tests
     * @param phase      the current TDD phase
     * @return true if tests passed unexpectedly in RED phase
     */
    public boolean isUnexpectedPass(String bashOutput, Phase phase) {
        if (bashOutput == null || bashOutput.isBlank()) {
            return false;
        }

        if (phase != Phase.RED) {
            return false;
        }

        // In RED phase, tests should fail. If they pass, it's unexpected.
        boolean testsPass = TEST_PASS_PATTERN.matcher(bashOutput).find();
        boolean noFailures = !isTestFailure(bashOutput) && !isCompilationError(bashOutput);

        return testsPass && noFailures;
    }

    /**
     * Detects if the output indicates a compilation error.
     *
     * @param bashOutput the output from a build/compile command
     * @return true if compilation errors are detected
     */
    public boolean isCompilationError(String bashOutput) {
        if (bashOutput == null || bashOutput.isBlank()) {
            return false;
        }

        return JAVA_COMPILATION_ERROR.matcher(bashOutput).find() ||
                TYPESCRIPT_COMPILATION_ERROR.matcher(bashOutput).find() ||
                PYTHON_SYNTAX_ERROR.matcher(bashOutput).find() ||
                GENERIC_COMPILATION_ERROR.matcher(bashOutput).find();
    }

    /**
     * Detects if an exception indicates a timeout error.
     *
     * @param exception the exception to check
     * @return true if the exception is or was caused by a timeout
     */
    public boolean isTimeout(Throwable exception) {
        if (exception == null) {
            return false;
        }

        // Check the exception itself
        if (exception instanceof TimeoutException) {
            return true;
        }

        // Check if it's a tool execution exception with timeout message
        if (exception instanceof ToolExecutionException) {
            String message = exception.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
        }

        // Check the exception message for timeout indicators
        String message = exception.getMessage();
        if (message != null && (
                message.toLowerCase().contains("timeout") ||
                        message.toLowerCase().contains("timed out"))) {
            return true;
        }

        // Check the cause recursively
        Throwable cause = exception.getCause();
        if (cause != null && cause != exception) {
            return isTimeout(cause);
        }

        return false;
    }

    /**
     * Classifies the error type from the output and exception.
     *
     * @param bashOutput the command output (may be null)
     * @param exception  the exception that occurred (may be null)
     * @param phase      the current TDD phase
     * @return the classified error type
     */
    public ErrorType classifyError(String bashOutput, Throwable exception, Phase phase) {
        // Check for timeout first (highest priority)
        if (isTimeout(exception)) {
            return ErrorType.TIMEOUT;
        }

        // Check for API/network errors
        if (exception != null) {
            String message = exception.getMessage();
            if (message != null) {
                if (message.contains("rate limit") || message.contains("429")) {
                    return ErrorType.RATE_LIMIT;
                }
                if (message.contains("network") || message.contains("connection")) {
                    return ErrorType.NETWORK;
                }
            }
        }

        // Check bash output for specific error types
        if (bashOutput != null) {
            if (isCompilationError(bashOutput)) {
                return ErrorType.COMPILATION;
            }

            if (isUnexpectedPass(bashOutput, phase)) {
                return ErrorType.UNEXPECTED_PASS;
            }

            if (isTestFailure(bashOutput)) {
                return ErrorType.TEST_FAILURE;
            }
        }

        // Default to unknown
        return ErrorType.UNKNOWN;
    }

    /**
     * Extracts a concise error message from the output.
     *
     * @param bashOutput the full command output
     * @param maxLength  maximum length of the extracted message
     * @return a concise error message
     */
    public String extractErrorMessage(String bashOutput, int maxLength) {
        if (bashOutput == null || bashOutput.isBlank()) {
            return "No output available";
        }

        // Look for specific error indicators
        String[] lines = bashOutput.split("\n");
        StringBuilder errorLines = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Capture lines that look like errors
            if (trimmed.toLowerCase().contains("error") ||
                    trimmed.toLowerCase().contains("failure") ||
                    trimmed.toLowerCase().contains("failed") ||
                    trimmed.startsWith("[ERROR]") ||
                    trimmed.contains("AssertionError") ||
                    trimmed.contains("Exception")) {

                if (errorLines.length() > 0) {
                    errorLines.append("\n");
                }
                errorLines.append(trimmed);

                if (errorLines.length() >= maxLength) {
                    break;
                }
            }
        }

        if (errorLines.length() == 0) {
            // Fall back to last few lines
            int startLine = Math.max(0, lines.length - 5);
            for (int i = startLine; i < lines.length; i++) {
                if (!lines[i].trim().isEmpty()) {
                    if (errorLines.length() > 0) {
                        errorLines.append("\n");
                    }
                    errorLines.append(lines[i].trim());
                }
            }
        }

        String result = errorLines.toString();
        if (result.length() > maxLength) {
            result = result.substring(0, maxLength - 3) + "...";
        }

        return result.isEmpty() ? "Unknown error" : result;
    }
}
