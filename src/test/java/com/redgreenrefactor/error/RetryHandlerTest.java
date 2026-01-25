package com.redgreenrefactor.error;

import com.redgreenrefactor.model.HandoffState;
import com.redgreenrefactor.model.Phase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RetryHandler")
class RetryHandlerTest {

    private RetryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RetryHandler();
    }

    @Nested
    @DisplayName("shouldRetry")
    class ShouldRetry {

        @Test
        @DisplayName("returns true when retry count is below max")
        void returnsTrueWhenBelowMax() {
            assertThat(handler.shouldRetry(0)).isTrue();
            assertThat(handler.shouldRetry(1)).isTrue();
            assertThat(handler.shouldRetry(2)).isTrue();
        }

        @Test
        @DisplayName("returns false when retry count equals max")
        void returnsFalseWhenEqualsMax() {
            assertThat(handler.shouldRetry(3)).isFalse();
        }

        @Test
        @DisplayName("returns false when retry count exceeds max")
        void returnsFalseWhenExceedsMax() {
            assertThat(handler.shouldRetry(5)).isFalse();
        }

        @Test
        @DisplayName("considers error type retriability")
        void considersErrorTypeRetriability() {
            assertThat(handler.shouldRetry(ErrorType.COMPILATION, 0)).isTrue();
            assertThat(handler.shouldRetry(ErrorType.TEST_FAILURE, 1)).isTrue();
            assertThat(handler.shouldRetry(ErrorType.TIMEOUT, 2)).isTrue();
        }

        @Test
        @DisplayName("returns false when max retries reached even for retriable errors")
        void returnsFalseWhenMaxRetriesReached() {
            assertThat(handler.shouldRetry(ErrorType.COMPILATION, 3)).isFalse();
        }
    }

    @Nested
    @DisplayName("getBackoffMs")
    class GetBackoffMs {

        @Test
        @DisplayName("returns correct backoff for each retry")
        void returnsCorrectBackoff() {
            assertThat(handler.getBackoffMs(1)).isEqualTo(1000);
            assertThat(handler.getBackoffMs(2)).isEqualTo(2000);
            assertThat(handler.getBackoffMs(3)).isEqualTo(4000);
        }

        @Test
        @DisplayName("returns last backoff value for attempts beyond array length")
        void returnsLastValueForExcessAttempts() {
            assertThat(handler.getBackoffMs(4)).isEqualTo(4000);
            assertThat(handler.getBackoffMs(10)).isEqualTo(4000);
        }

        @Test
        @DisplayName("returns 0 for non-positive attempts")
        void returnsZeroForNonPositive() {
            assertThat(handler.getBackoffMs(0)).isEqualTo(0);
            assertThat(handler.getBackoffMs(-1)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("prepareForRetry")
    class PrepareForRetry {

        @Test
        @DisplayName("increments retry count")
        void incrementsRetryCount() {
            HandoffState state = HandoffState.initial();
            assertThat(state.retryCount()).isEqualTo(0);

            HandoffState updated = handler.prepareForRetry(state, ErrorType.COMPILATION, "error");
            assertThat(updated.retryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("sets error message")
        void setsErrorMessage() {
            HandoffState state = HandoffState.initial();
            HandoffState updated = handler.prepareForRetry(state, ErrorType.COMPILATION,
                    "[ERROR] Cannot find symbol");

            assertThat(updated.error()).contains("Cannot find symbol");
        }

        @Test
        @DisplayName("sets error details with type")
        void setsErrorDetails() {
            HandoffState state = HandoffState.initial();
            HandoffState updated = handler.prepareForRetry(state, ErrorType.TEST_FAILURE, "FAILURES!");

            assertThat(updated.errorDetails()).isNotNull();
            assertThat(updated.errorDetails().type()).isEqualTo("TEST_FAILURE");
        }

        @Test
        @DisplayName("preserves other state fields")
        void preservesOtherFields() {
            HandoffState state = HandoffState.builder()
                    .phase(Phase.GREEN)
                    .cycleNumber(3)
                    .build();

            HandoffState updated = handler.prepareForRetry(state, ErrorType.COMPILATION, "error");

            assertThat(updated.phase()).isEqualTo(Phase.GREEN);
            assertThat(updated.cycleNumber()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("buildRetryContext")
    class BuildRetryContext {

        @Test
        @DisplayName("includes retry count information")
        void includesRetryCount() {
            HandoffState state = HandoffState.builder()
                    .retryCount(2)
                    .error("Some error")
                    .build();

            String context = handler.buildRetryContext(state, Phase.GREEN, ErrorType.COMPILATION);

            assertThat(context).contains("retry attempt 2");
            assertThat(context).contains("of 3");
        }

        @Test
        @DisplayName("includes error type description")
        void includesErrorType() {
            HandoffState state = HandoffState.builder()
                    .retryCount(1)
                    .error("error")
                    .build();

            String context = handler.buildRetryContext(state, Phase.GREEN, ErrorType.COMPILATION);

            assertThat(context).contains("Compilation error");
        }

        @Test
        @DisplayName("includes error details when present")
        void includesErrorDetails() {
            HandoffState state = HandoffState.builder()
                    .retryCount(1)
                    .error("Cannot find symbol 'foo'")
                    .build();

            String context = handler.buildRetryContext(state, Phase.GREEN, ErrorType.COMPILATION);

            assertThat(context).contains("Cannot find symbol 'foo'");
        }

        @Test
        @DisplayName("includes phase-specific guidance for unexpected pass")
        void includesGuidanceForUnexpectedPass() {
            HandoffState state = HandoffState.builder()
                    .retryCount(1)
                    .error("Tests passed")
                    .build();

            String context = handler.buildRetryContext(state, Phase.RED, ErrorType.UNEXPECTED_PASS);

            assertThat(context).contains("proper assertion");
            assertThat(context).contains("fail");
        }

        @Test
        @DisplayName("includes phase-specific guidance for test failure in GREEN")
        void includesGuidanceForTestFailureInGreen() {
            HandoffState state = HandoffState.builder()
                    .retryCount(1)
                    .error("Tests failed")
                    .build();

            String context = handler.buildRetryContext(state, Phase.GREEN, ErrorType.TEST_FAILURE);

            assertThat(context).contains("fix the implementation");
        }
    }

    @Nested
    @DisplayName("executeWithRetry")
    class ExecuteWithRetry {

        @Test
        @DisplayName("returns result on first success")
        void returnsOnFirstSuccess() throws Exception {
            String result = handler.executeWithRetry(
                    () -> "success",
                    ErrorType.UNKNOWN,
                    3
            );
            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("retries on failure and eventually succeeds")
        void retriesAndSucceeds() throws Exception {
            AtomicInteger attempts = new AtomicInteger(0);

            String result = handler.executeWithRetry(
                    () -> {
                        if (attempts.incrementAndGet() < 3) {
                            throw new RuntimeException("fail");
                        }
                        return "success after retries";
                    },
                    ErrorType.UNKNOWN,
                    3
            );

            assertThat(result).isEqualTo("success after retries");
            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("throws RetryExhaustedException when all attempts fail")
        void throwsWhenExhausted() {
            assertThatThrownBy(() ->
                    handler.executeWithRetry(
                            () -> {
                                throw new RuntimeException("always fails");
                            },
                            ErrorType.UNKNOWN,
                            3
                    )
            )
                    .isInstanceOf(RetryExhaustedException.class)
                    .hasMessageContaining("3 attempts");
        }

        @Test
        @DisplayName("includes attempt count in exception")
        void includesAttemptCount() {
            try {
                handler.executeWithRetry(
                        () -> {
                            throw new RuntimeException("fail");
                        },
                        ErrorType.UNKNOWN,
                        2
                );
            } catch (RetryExhaustedException e) {
                assertThat(e.getAttemptsMade()).isEqualTo(2);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nested
    @DisplayName("custom configuration")
    class CustomConfiguration {

        @Test
        @DisplayName("respects custom max retries")
        void respectsCustomMaxRetries() {
            RetryHandler customHandler = new RetryHandler(5, new long[]{100, 200}, new TDDErrorHandler());

            assertThat(customHandler.getMaxRetries()).isEqualTo(5);
            assertThat(customHandler.shouldRetry(4)).isTrue();
            assertThat(customHandler.shouldRetry(5)).isFalse();
        }

        @Test
        @DisplayName("respects custom backoff values")
        void respectsCustomBackoff() {
            RetryHandler customHandler = new RetryHandler(3, new long[]{100, 500, 2000}, new TDDErrorHandler());

            assertThat(customHandler.getBackoffMs(1)).isEqualTo(100);
            assertThat(customHandler.getBackoffMs(2)).isEqualTo(500);
            assertThat(customHandler.getBackoffMs(3)).isEqualTo(2000);
        }

        @Test
        @DisplayName("rejects negative max retries")
        void rejectsNegativeMaxRetries() {
            assertThatThrownBy(() -> new RetryHandler(-1, null, new TDDErrorHandler()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
