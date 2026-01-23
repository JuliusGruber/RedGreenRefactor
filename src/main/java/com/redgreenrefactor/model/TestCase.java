package com.redgreenrefactor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single test case in the TDD workflow.
 *
 * @param description A human-readable description of what the test verifies
 * @param testFile    The path to the test file (e.g., src/test/java/...)
 * @param implFile    The path to the implementation file (e.g., src/main/java/...)
 */
public record TestCase(
        @JsonProperty("description") String description,
        @JsonProperty("testFile") String testFile,
        @JsonProperty("implFile") String implFile
) {
    public TestCase {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description cannot be null or blank");
        }
        if (testFile == null || testFile.isBlank()) {
            throw new IllegalArgumentException("testFile cannot be null or blank");
        }
        if (implFile == null || implFile.isBlank()) {
            throw new IllegalArgumentException("implFile cannot be null or blank");
        }
    }
}
