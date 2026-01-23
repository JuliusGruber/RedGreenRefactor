package com.redgreenrefactor.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolDispatcherTest {

    private ToolDispatcher dispatcher;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        dispatcher = new ToolDispatcher(tempDir);
    }

    @Test
    void dispatch_handlesReadTool() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "test content");

        ToolResult result = dispatcher.dispatch("Read", Map.of("file_path", file.toString()));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("test content");
    }

    @Test
    void dispatch_handlesWriteTool() throws Exception {
        Path file = tempDir.resolve("new.txt");

        ToolResult result = dispatcher.dispatch("Write", Map.of(
                "file_path", file.toString(),
                "content", "new content"
        ));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("new content");
    }

    @Test
    void dispatch_handlesEditTool() throws Exception {
        Path file = tempDir.resolve("edit.txt");
        Files.writeString(file, "old text");

        ToolResult result = dispatcher.dispatch("Edit", Map.of(
                "file_path", file.toString(),
                "old_string", "old",
                "new_string", "new"
        ));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("new text");
    }

    @Test
    void dispatch_handlesBashTool() throws Exception {
        ToolResult result = dispatcher.dispatch("Bash", Map.of("command", "echo test"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("test");
    }

    @Test
    void dispatch_handlesGlobTool() throws Exception {
        Files.writeString(tempDir.resolve("file.txt"), "content");

        ToolResult result = dispatcher.dispatch("Glob", Map.of("pattern", "*.txt"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("file.txt");
    }

    @Test
    void dispatch_handlesGrepTool() throws Exception {
        Files.writeString(tempDir.resolve("search.txt"), "find this line");

        ToolResult result = dispatcher.dispatch("Grep", Map.of("pattern", "find"));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("find this line");
    }

    @Test
    void dispatch_throwsForUnknownTool() {
        assertThatThrownBy(() -> dispatcher.dispatch("Unknown", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Unknown tool");
    }

    @Test
    void hasHandler_returnsTrueForRegisteredTools() {
        assertThat(dispatcher.hasHandler("Read")).isTrue();
        assertThat(dispatcher.hasHandler("Write")).isTrue();
        assertThat(dispatcher.hasHandler("Edit")).isTrue();
        assertThat(dispatcher.hasHandler("Bash")).isTrue();
        assertThat(dispatcher.hasHandler("Glob")).isTrue();
        assertThat(dispatcher.hasHandler("Grep")).isTrue();
    }

    @Test
    void hasHandler_returnsFalseForUnknownTools() {
        assertThat(dispatcher.hasHandler("Unknown")).isFalse();
        assertThat(dispatcher.hasHandler("")).isFalse();
    }

    @Test
    void constructor_acceptsCustomTimeout() throws Exception {
        ToolDispatcher customDispatcher = new ToolDispatcher(tempDir, 60);

        ToolResult result = customDispatcher.dispatch("Bash", Map.of("command", "echo test"));

        assertThat(result.success()).isTrue();
    }

    @Test
    void registerHandler_addsCustomHandler() throws Exception {
        ToolExecutor customHandler = new ToolExecutor() {
            @Override
            public String getToolName() {
                return "Custom";
            }

            @Override
            public ToolResult execute(Map<String, Object> inputs) {
                return ToolResult.success("custom result");
            }
        };

        dispatcher.registerHandler(customHandler);

        assertThat(dispatcher.hasHandler("Custom")).isTrue();
        ToolResult result = dispatcher.dispatch("Custom", Map.of());
        assertThat(result.output()).isEqualTo("custom result");
    }
}
