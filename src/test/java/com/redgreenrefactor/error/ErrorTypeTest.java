package com.redgreenrefactor.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorType")
class ErrorTypeTest {

    @Test
    @DisplayName("all error types have descriptions")
    void allTypesHaveDescriptions() {
        for (ErrorType type : ErrorType.values()) {
            assertThat(type.getDescription())
                    .as("Description for " + type)
                    .isNotNull()
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("all error types are retriable")
    void allTypesAreRetriable() {
        for (ErrorType type : ErrorType.values()) {
            assertThat(type.isRetriable())
                    .as("isRetriable for " + type)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("only TEST_FAILURE requires rollback")
    void onlyTestFailureRequiresRollback() {
        assertThat(ErrorType.TEST_FAILURE.requiresRollback()).isTrue();

        assertThat(ErrorType.COMPILATION.requiresRollback()).isFalse();
        assertThat(ErrorType.UNEXPECTED_PASS.requiresRollback()).isFalse();
        assertThat(ErrorType.TIMEOUT.requiresRollback()).isFalse();
        assertThat(ErrorType.RATE_LIMIT.requiresRollback()).isFalse();
        assertThat(ErrorType.NETWORK.requiresRollback()).isFalse();
        assertThat(ErrorType.UNKNOWN.requiresRollback()).isFalse();
    }

    @Test
    @DisplayName("error types have expected descriptions")
    void hasExpectedDescriptions() {
        assertThat(ErrorType.COMPILATION.getDescription()).contains("compile");
        assertThat(ErrorType.TEST_FAILURE.getDescription()).contains("Test");
        assertThat(ErrorType.UNEXPECTED_PASS.getDescription()).contains("RED");
        assertThat(ErrorType.TIMEOUT.getDescription()).contains("timeout");
        assertThat(ErrorType.RATE_LIMIT.getDescription()).contains("rate limit");
        assertThat(ErrorType.NETWORK.getDescription()).contains("Network");
        assertThat(ErrorType.UNKNOWN.getDescription()).contains("Unknown");
    }
}
