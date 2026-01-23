package com.redgreenrefactor.orchestrator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorExceptionTest {

    @Test
    void constructorWithMessage_setsMessage() {
        OrchestratorException exception = new OrchestratorException("Test error");

        assertThat(exception.getMessage()).isEqualTo("Test error");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void constructorWithMessageAndCause_setsBoth() {
        RuntimeException cause = new RuntimeException("Root cause");
        OrchestratorException exception = new OrchestratorException("Test error", cause);

        assertThat(exception.getMessage()).isEqualTo("Test error");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void isRuntimeException() {
        OrchestratorException exception = new OrchestratorException("Test");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
