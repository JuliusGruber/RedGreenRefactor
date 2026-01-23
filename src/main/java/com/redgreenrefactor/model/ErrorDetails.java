package com.redgreenrefactor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contains detailed information about an error that occurred during workflow execution.
 *
 * @param type    The type of error (e.g., "TestFailure", "CompilationError", "Timeout")
 * @param message A detailed message describing the error
 */
public record ErrorDetails(
        @JsonProperty("type") String type,
        @JsonProperty("message") String message
) {
    public ErrorDetails {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type cannot be null or blank");
        }
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
    }
}
