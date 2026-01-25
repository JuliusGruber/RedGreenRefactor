package com.redgreenrefactor.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetryExhaustedException")
class RetryExhaustedExceptionTest {

    @Test
    @DisplayName("stores message and cause")
    void storesMessageAndCause() {
        Throwable cause = new RuntimeException("underlying");
        RetryExhaustedException exception = new RetryExhaustedException("failed", cause, 3);

        assertThat(exception.getMessage()).isEqualTo("failed");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("stores attempts made")
    void storesAttemptsMade() {
        RetryExhaustedException exception = new RetryExhaustedException("failed", 5);

        assertThat(exception.getAttemptsMade()).isEqualTo(5);
    }

    @Test
    @DisplayName("works without cause")
    void worksWithoutCause() {
        RetryExhaustedException exception = new RetryExhaustedException("failed", 3);

        assertThat(exception.getMessage()).isEqualTo("failed");
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getAttemptsMade()).isEqualTo(3);
    }
}
