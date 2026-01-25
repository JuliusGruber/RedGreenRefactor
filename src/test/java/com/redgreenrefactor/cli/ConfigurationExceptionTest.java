package com.redgreenrefactor.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ConfigurationExceptionTest {

    @Test
    void constructor_withMessage() {
        ConfigurationException exception = new ConfigurationException("Test error");

        assertThat(exception.getMessage()).isEqualTo("Test error");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void constructor_withMessageAndCause() {
        RuntimeException cause = new RuntimeException("Underlying error");
        ConfigurationException exception = new ConfigurationException("Test error", cause);

        assertThat(exception.getMessage()).isEqualTo("Test error");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void isCheckedException() {
        assertThat(Exception.class.isAssignableFrom(ConfigurationException.class)).isTrue();
        assertThat(RuntimeException.class.isAssignableFrom(ConfigurationException.class)).isFalse();
    }
}
