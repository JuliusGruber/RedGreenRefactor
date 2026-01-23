package com.redgreenrefactor.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInvocationExceptionTest {

    @Test
    void constructor_withMessage() {
        AgentInvocationException exception = new AgentInvocationException("Test error");

        assertThat(exception.getMessage()).isEqualTo("Test error");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void constructor_withMessageAndCause() {
        RuntimeException cause = new RuntimeException("Root cause");
        AgentInvocationException exception = new AgentInvocationException("Test error", cause);

        assertThat(exception.getMessage()).isEqualTo("Test error");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void isRuntimeException() {
        AgentInvocationException exception = new AgentInvocationException("Test");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
