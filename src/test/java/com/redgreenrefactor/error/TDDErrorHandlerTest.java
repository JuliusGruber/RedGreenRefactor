package com.redgreenrefactor.error;

import com.redgreenrefactor.model.Phase;
import com.redgreenrefactor.tool.ToolExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TDDErrorHandler")
class TDDErrorHandlerTest {

    private TDDErrorHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TDDErrorHandler();
    }

    @Nested
    @DisplayName("isTestFailure")
    class IsTestFailure {

        @Test
        @DisplayName("detects JUnit failure output")
        void detectsJUnitFailure() {
            String output = """
                    Tests run: 5, Failures: 1, Errors: 0, Skipped: 0
                    [ERROR] FAILURES!
                    """;
            assertThat(handler.isTestFailure(output)).isTrue();
        }

        @Test
        @DisplayName("detects Maven test failure")
        void detectsMavenTestFailure() {
            String output = """
                    [ERROR] Tests run: 3, Failures: 2, Errors: 0, Skipped: 0
                    [ERROR] There are test failures.
                    """;
            assertThat(handler.isTestFailure(output)).isTrue();
        }

        @Test
        @DisplayName("detects Gradle test failure")
        void detectsGradleTestFailure() {
            String output = """
                    > Task :test FAILED
                    3 tests completed, 1 failed
                    """;
            assertThat(handler.isTestFailure(output)).isTrue();
        }

        @Test
        @DisplayName("detects npm/Jest test failure")
        void detectsNpmTestFailure() {
            String output = """
                    FAIL src/test.spec.ts
                    2 failing
                    AssertionError: expected 'foo' to equal 'bar'
                    """;
            assertThat(handler.isTestFailure(output)).isTrue();
        }

        @Test
        @DisplayName("detects pytest failure")
        void detectsPytestFailure() {
            String output = """
                    ============================= FAILURES =============================
                    FAILED test_example.py::test_something - AssertionError
                    1 failed, 2 passed
                    """;
            assertThat(handler.isTestFailure(output)).isTrue();
        }

        @Test
        @DisplayName("returns false for passing tests")
        void returnsFalseForPassingTests() {
            String output = """
                    Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
                    BUILD SUCCESS
                    """;
            assertThat(handler.isTestFailure(output)).isFalse();
        }

        @Test
        @DisplayName("returns false for null input")
        void returnsFalseForNull() {
            assertThat(handler.isTestFailure(null)).isFalse();
        }

        @Test
        @DisplayName("returns false for empty input")
        void returnsFalseForEmpty() {
            assertThat(handler.isTestFailure("")).isFalse();
            assertThat(handler.isTestFailure("   ")).isFalse();
        }
    }

    @Nested
    @DisplayName("isUnexpectedPass")
    class IsUnexpectedPass {

        @Test
        @DisplayName("detects unexpected pass in RED phase")
        void detectsUnexpectedPassInRedPhase() {
            String output = """
                    Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
                    BUILD SUCCESS
                    """;
            assertThat(handler.isUnexpectedPass(output, Phase.RED)).isTrue();
        }

        @Test
        @DisplayName("returns false for failing tests in RED phase")
        void returnsFalseForFailingTestsInRed() {
            String output = """
                    Tests run: 5, Failures: 1, Errors: 0, Skipped: 0
                    FAILURES!
                    """;
            assertThat(handler.isUnexpectedPass(output, Phase.RED)).isFalse();
        }

        @Test
        @DisplayName("returns false for GREEN phase even if tests pass")
        void returnsFalseForGreenPhase() {
            String output = """
                    Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
                    BUILD SUCCESS
                    """;
            assertThat(handler.isUnexpectedPass(output, Phase.GREEN)).isFalse();
        }

        @Test
        @DisplayName("returns false for REFACTOR phase even if tests pass")
        void returnsFalseForRefactorPhase() {
            String output = "BUILD SUCCESSFUL";
            assertThat(handler.isUnexpectedPass(output, Phase.REFACTOR)).isFalse();
        }

        @Test
        @DisplayName("returns false for compilation errors in RED phase")
        void returnsFalseForCompilationErrorsInRed() {
            String output = """
                    [ERROR] COMPILATION ERROR
                    error: cannot find symbol
                    """;
            assertThat(handler.isUnexpectedPass(output, Phase.RED)).isFalse();
        }
    }

    @Nested
    @DisplayName("isCompilationError")
    class IsCompilationError {

        @Test
        @DisplayName("detects Java compilation error")
        void detectsJavaCompilationError() {
            String output = """
                    [ERROR] /src/main/java/Foo.java:[15,10] error: cannot find symbol
                    [ERROR] COMPILATION ERROR
                    """;
            assertThat(handler.isCompilationError(output)).isTrue();
        }

        @Test
        @DisplayName("detects TypeScript compilation error")
        void detectsTypeScriptError() {
            String output = """
                    error TS2304: Cannot find name 'foo'.
                    src/app.ts(10,5): error TS2339: Property 'bar' does not exist on type 'Baz'.
                    """;
            assertThat(handler.isCompilationError(output)).isTrue();
        }

        @Test
        @DisplayName("detects Python syntax error")
        void detectsPythonSyntaxError() {
            String output = """
                    File "test.py", line 5
                        def foo(
                              ^
                    SyntaxError: unexpected EOF while parsing
                    """;
            assertThat(handler.isCompilationError(output)).isTrue();
        }

        @Test
        @DisplayName("returns false for test output")
        void returnsFalseForTestOutput() {
            String output = """
                    Tests run: 5, Failures: 1, Errors: 0
                    """;
            assertThat(handler.isCompilationError(output)).isFalse();
        }

        @Test
        @DisplayName("returns false for null input")
        void returnsFalseForNull() {
            assertThat(handler.isCompilationError(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isTimeout")
    class IsTimeout {

        @Test
        @DisplayName("detects TimeoutException")
        void detectsTimeoutException() {
            assertThat(handler.isTimeout(new TimeoutException("timed out"))).isTrue();
        }

        @Test
        @DisplayName("detects timeout in ToolExecutionException")
        void detectsToolExecutionTimeout() {
            var exception = new ToolExecutionException("Command timed out after 120s", null);
            assertThat(handler.isTimeout(exception)).isTrue();
        }

        @Test
        @DisplayName("detects timeout in exception message")
        void detectsTimeoutInMessage() {
            assertThat(handler.isTimeout(new RuntimeException("Operation timeout occurred"))).isTrue();
        }

        @Test
        @DisplayName("detects timeout in nested cause")
        void detectsTimeoutInCause() {
            var cause = new TimeoutException("inner timeout");
            var wrapper = new RuntimeException("outer", cause);
            assertThat(handler.isTimeout(wrapper)).isTrue();
        }

        @Test
        @DisplayName("returns false for non-timeout exceptions")
        void returnsFalseForNonTimeout() {
            assertThat(handler.isTimeout(new RuntimeException("something else"))).isFalse();
        }

        @Test
        @DisplayName("returns false for null")
        void returnsFalseForNull() {
            assertThat(handler.isTimeout(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("classifyError")
    class ClassifyError {

        @Test
        @DisplayName("classifies timeout errors")
        void classifiesTimeoutErrors() {
            var exception = new TimeoutException("timed out");
            assertThat(handler.classifyError(null, exception, Phase.GREEN))
                    .isEqualTo(ErrorType.TIMEOUT);
        }

        @Test
        @DisplayName("classifies rate limit errors")
        void classifiesRateLimitErrors() {
            var exception = new RuntimeException("rate limit exceeded (429)");
            assertThat(handler.classifyError(null, exception, Phase.GREEN))
                    .isEqualTo(ErrorType.RATE_LIMIT);
        }

        @Test
        @DisplayName("classifies compilation errors")
        void classifiesCompilationErrors() {
            String output = "[ERROR] COMPILATION ERROR";
            assertThat(handler.classifyError(output, null, Phase.GREEN))
                    .isEqualTo(ErrorType.COMPILATION);
        }

        @Test
        @DisplayName("classifies unexpected pass in RED phase")
        void classifiesUnexpectedPass() {
            String output = "BUILD SUCCESS\nTests run: 5, Failures: 0";
            assertThat(handler.classifyError(output, null, Phase.RED))
                    .isEqualTo(ErrorType.UNEXPECTED_PASS);
        }

        @Test
        @DisplayName("classifies test failures")
        void classifiesTestFailures() {
            String output = "FAILURES!\nTests run: 5, Failures: 1";
            assertThat(handler.classifyError(output, null, Phase.GREEN))
                    .isEqualTo(ErrorType.TEST_FAILURE);
        }

        @Test
        @DisplayName("returns UNKNOWN for unrecognized errors")
        void returnsUnknownForUnrecognized() {
            assertThat(handler.classifyError("some random output", null, Phase.GREEN))
                    .isEqualTo(ErrorType.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("extractErrorMessage")
    class ExtractErrorMessage {

        @Test
        @DisplayName("extracts error lines from output")
        void extractsErrorLines() {
            String output = """
                    Starting build...
                    [ERROR] Cannot find symbol
                    [ERROR] Compilation failed
                    Done.
                    """;
            String message = handler.extractErrorMessage(output, 200);
            assertThat(message).contains("Cannot find symbol");
            assertThat(message).contains("Compilation failed");
        }

        @Test
        @DisplayName("truncates long messages")
        void truncatesLongMessages() {
            String output = "[ERROR] " + "x".repeat(1000);
            String message = handler.extractErrorMessage(output, 100);
            assertThat(message.length()).isLessThanOrEqualTo(100);
            assertThat(message).endsWith("...");
        }

        @Test
        @DisplayName("returns last lines if no error markers found")
        void returnsLastLinesIfNoErrorMarkers() {
            String output = """
                    line 1
                    line 2
                    line 3
                    line 4
                    final line
                    """;
            String message = handler.extractErrorMessage(output, 200);
            assertThat(message).contains("final line");
        }

        @Test
        @DisplayName("handles null input")
        void handlesNullInput() {
            assertThat(handler.extractErrorMessage(null, 100)).isEqualTo("No output available");
        }

        @Test
        @DisplayName("handles empty input")
        void handlesEmptyInput() {
            assertThat(handler.extractErrorMessage("", 100)).isEqualTo("No output available");
        }
    }
}
