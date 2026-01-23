package com.redgreenrefactor.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorDetailsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldSerializeToJson() throws Exception {
        ErrorDetails error = new ErrorDetails("TestFailure", "Expected 200 but got 404");

        String json = objectMapper.writeValueAsString(error);

        assertThat(json).contains("\"type\":\"TestFailure\"");
        assertThat(json).contains("\"message\":\"Expected 200 but got 404\"");
    }

    @Test
    void shouldDeserializeFromJson() throws Exception {
        String json = """
                {
                    "type": "CompilationError",
                    "message": "Cannot find symbol: Calculator"
                }
                """;

        ErrorDetails error = objectMapper.readValue(json, ErrorDetails.class);

        assertThat(error.type()).isEqualTo("CompilationError");
        assertThat(error.message()).isEqualTo("Cannot find symbol: Calculator");
    }

    @Test
    void shouldRoundTrip() throws Exception {
        ErrorDetails original = new ErrorDetails("Timeout", "Command exceeded 120s limit");

        String json = objectMapper.writeValueAsString(original);
        ErrorDetails deserialized = objectMapper.readValue(json, ErrorDetails.class);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void shouldThrowOnNullType() {
        assertThatThrownBy(() -> new ErrorDetails(null, "message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void shouldThrowOnBlankType() {
        assertThatThrownBy(() -> new ErrorDetails("  ", "message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void shouldThrowOnNullMessage() {
        assertThatThrownBy(() -> new ErrorDetails("Type", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }

    @Test
    void shouldAllowEmptyMessage() {
        ErrorDetails error = new ErrorDetails("Type", "");
        assertThat(error.message()).isEmpty();
    }

    @Test
    void shouldHaveProperEqualsHashCodeToString() {
        ErrorDetails e1 = new ErrorDetails("Type", "msg");
        ErrorDetails e2 = new ErrorDetails("Type", "msg");
        ErrorDetails e3 = new ErrorDetails("Other", "msg");

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
        assertThat(e1).isNotEqualTo(e3);
        assertThat(e1.toString()).contains("Type", "msg");
    }
}
