package com.redgreenrefactor.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GrepToolHandlerTest {

    private GrepToolHandler handler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        handler = new GrepToolHandler(tempDir);
    }

    @Test
    void getToolName_returnsGrep() {
        assertThat(handler.getToolName()).isEqualTo("Grep");
    }

    @Test
    void execute_findsMatchingLines() throws Exception {
        Files.writeString(tempDir.resolve("test.txt"), "Line 1 with foo\nLine 2\nLine 3 with foo");

        ToolResult result = handler.execute(Map.of("pattern", "foo"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Line 1 with foo");
        assertThat(result.output()).contains("Line 3 with foo");
        assertThat(result.output()).doesNotContain("Line 2");
    }

    @Test
    void execute_returnsNoMatchesMessage() throws Exception {
        Files.writeString(tempDir.resolve("test.txt"), "Hello World");

        ToolResult result = handler.execute(Map.of("pattern", "nonexistent"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("No matches found");
    }

    @Test
    void execute_searchesMultipleFiles() throws Exception {
        Files.writeString(tempDir.resolve("file1.txt"), "foo in file 1");
        Files.writeString(tempDir.resolve("file2.txt"), "bar in file 2");
        Files.writeString(tempDir.resolve("file3.txt"), "foo in file 3");

        ToolResult result = handler.execute(Map.of("pattern", "foo"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("file1.txt");
        assertThat(result.output()).contains("file3.txt");
        assertThat(result.output()).doesNotContain("file2.txt");
    }

    @Test
    void execute_supportsRegex() throws Exception {
        Files.writeString(tempDir.resolve("test.txt"), "error123\nwarning456\nerror789");

        ToolResult result = handler.execute(Map.of("pattern", "error\\d+"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("error123");
        assertThat(result.output()).contains("error789");
        assertThat(result.output()).doesNotContain("warning");
    }

    @Test
    void execute_returnsFailureForInvalidRegex() throws Exception {
        ToolResult result = handler.execute(Map.of("pattern", "[invalid"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Invalid regex");
    }

    @Test
    void execute_returnsFailureForMissingPattern() throws Exception {
        ToolResult result = handler.execute(Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("pattern is required");
    }

    @Test
    void execute_filtersWithGlob() throws Exception {
        Files.writeString(tempDir.resolve("file.java"), "public class Foo");
        Files.writeString(tempDir.resolve("file.txt"), "class Foo text");

        ToolResult result = handler.execute(Map.of(
                "pattern", "class",
                "glob", "*.java"
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("file.java");
        assertThat(result.output()).doesNotContain("file.txt");
    }

    @Test
    void execute_searchesSpecificFile() throws Exception {
        Files.writeString(tempDir.resolve("target.txt"), "find me");
        Files.writeString(tempDir.resolve("other.txt"), "find me too");

        ToolResult result = handler.execute(Map.of(
                "pattern", "find",
                "path", tempDir.resolve("target.txt").toString()
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("target.txt");
        assertThat(result.output()).doesNotContain("other.txt");
    }

    @Test
    void execute_includesLineNumbers() throws Exception {
        Files.writeString(tempDir.resolve("test.txt"), "line1\nmatch here\nline3");

        ToolResult result = handler.execute(Map.of("pattern", "match"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains(":2:");  // Line number 2
    }

    @Test
    void execute_skipsHiddenDirectories() throws Exception {
        Path hiddenDir = tempDir.resolve(".hidden");
        Files.createDirectories(hiddenDir);
        Files.writeString(hiddenDir.resolve("secret.txt"), "secret content");
        Files.writeString(tempDir.resolve("visible.txt"), "secret content");

        ToolResult result = handler.execute(Map.of("pattern", "secret"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("visible.txt");
        assertThat(result.output()).doesNotContain(".hidden");
    }

    @Test
    void execute_skipsCommonBuildDirectories() throws Exception {
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("generated.txt"), "findme");
        Files.writeString(tempDir.resolve("source.txt"), "findme");

        ToolResult result = handler.execute(Map.of("pattern", "findme"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("source.txt");
        assertThat(result.output()).doesNotContain("target");
    }
}
