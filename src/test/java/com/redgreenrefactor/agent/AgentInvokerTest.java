package com.redgreenrefactor.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessagesClient;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.redgreenrefactor.model.AgentConfig;
import com.redgreenrefactor.tool.ToolDispatcher;
import com.redgreenrefactor.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentInvokerTest {

    private AnthropicClient mockClient;
    private MessagesClient mockMessagesClient;
    private ToolDispatcher mockToolDispatcher;
    private AgentInvoker invoker;

    @BeforeEach
    void setUp() {
        mockClient = mock(AnthropicClient.class);
        mockMessagesClient = mock(MessagesClient.class);
        mockToolDispatcher = mock(ToolDispatcher.class);

        when(mockClient.messages()).thenReturn(mockMessagesClient);

        invoker = new AgentInvoker(mockClient, mockToolDispatcher);
    }

    @Test
    void constructor_throwsOnNullClient() {
        assertThatThrownBy(() -> new AgentInvoker(null, mockToolDispatcher))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("client");
    }

    @Test
    void constructor_throwsOnNullToolDispatcher() {
        assertThatThrownBy(() -> new AgentInvoker(mockClient, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("toolDispatcher");
    }

    @Test
    void invoke_throwsOnNullConfig() {
        assertThatThrownBy(() -> invoker.invoke(null, "test prompt"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config");
    }

    @Test
    void invoke_throwsOnNullPrompt() {
        AgentConfig config = createTestConfig();
        assertThatThrownBy(() -> invoker.invoke(config, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("prompt");
    }

    @Test
    void invoke_returnsTextResponse_whenNoToolUse() {
        // Setup
        AgentConfig config = createTestConfig();
        Message response = createTextResponse("Hello, world!");

        when(mockMessagesClient.create(any(MessageCreateParams.class))).thenReturn(response);

        // Execute
        AgentInvoker.AgentResponse result = invoker.invoke(config, "Test prompt");

        // Verify
        assertThat(result.responseText()).isEqualTo("Hello, world!");
        assertThat(result.iterationCount()).isEqualTo(1);
        assertThat(result.conversationHistory()).hasSize(2); // User + Assistant
    }

    @Test
    void invoke_executesToolAndContinues() {
        // Setup
        AgentConfig config = createTestConfig();

        // First response: tool use
        Message toolUseResponse = createToolUseResponse("Read", "tool-123",
                Map.of("file_path", "/test/file.txt"));

        // Second response: text after tool result
        Message textResponse = createTextResponse("File content processed");

        when(mockMessagesClient.create(any(MessageCreateParams.class)))
                .thenReturn(toolUseResponse)
                .thenReturn(textResponse);

        when(mockToolDispatcher.dispatch("Read", Map.of("file_path", "/test/file.txt")))
                .thenReturn(ToolResult.success("file content"));

        // Execute
        AgentInvoker.AgentResponse result = invoker.invoke(config, "Read the file");

        // Verify
        assertThat(result.responseText()).isEqualTo("File content processed");
        assertThat(result.iterationCount()).isEqualTo(2);
        verify(mockToolDispatcher).dispatch("Read", Map.of("file_path", "/test/file.txt"));
    }

    @Test
    void invoke_handlesToolError() {
        // Setup
        AgentConfig config = createTestConfig();

        Message toolUseResponse = createToolUseResponse("Read", "tool-123",
                Map.of("file_path", "/nonexistent.txt"));
        Message textResponse = createTextResponse("File not found, trying another approach");

        when(mockMessagesClient.create(any(MessageCreateParams.class)))
                .thenReturn(toolUseResponse)
                .thenReturn(textResponse);

        when(mockToolDispatcher.dispatch("Read", Map.of("file_path", "/nonexistent.txt")))
                .thenReturn(ToolResult.failure("File not found"));

        // Execute
        AgentInvoker.AgentResponse result = invoker.invoke(config, "Read the file");

        // Verify
        assertThat(result.responseText()).isEqualTo("File not found, trying another approach");
    }

    @Test
    void invoke_handlesToolException() {
        // Setup
        AgentConfig config = createTestConfig();

        Message toolUseResponse = createToolUseResponse("Bash", "tool-123",
                Map.of("command", "invalid"));
        Message textResponse = createTextResponse("Command failed");

        when(mockMessagesClient.create(any(MessageCreateParams.class)))
                .thenReturn(toolUseResponse)
                .thenReturn(textResponse);

        when(mockToolDispatcher.dispatch("Bash", Map.of("command", "invalid")))
                .thenThrow(new RuntimeException("Execution failed"));

        // Execute
        AgentInvoker.AgentResponse result = invoker.invoke(config, "Run command");

        // Verify - should continue after exception
        assertThat(result.responseText()).isEqualTo("Command failed");
    }

    @Test
    void invoke_passesSystemPromptAndModel() {
        // Setup
        AgentConfig config = AgentConfig.builder()
                .name("TestAgent")
                .description("Test")
                .systemPrompt("You are a test agent")
                .model("claude-opus-4-5-20251101")
                .build();

        Message response = createTextResponse("Done");
        when(mockMessagesClient.create(any(MessageCreateParams.class))).thenReturn(response);

        // Execute
        invoker.invoke(config, "Test");

        // Verify
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(mockMessagesClient).create(captor.capture());

        MessageCreateParams params = captor.getValue();
        assertThat(params.model().toString()).isEqualTo("claude-opus-4-5-20251101");
        assertThat(params.system().get().toString()).contains("You are a test agent");
    }

    private AgentConfig createTestConfig() {
        return AgentConfig.builder()
                .name("TestAgent")
                .description("A test agent")
                .systemPrompt("You are a test agent")
                .build();
    }

    private Message createTextResponse(String text) {
        TextBlock textBlock = TextBlock.builder()
                .text(text)
                .build();

        return Message.builder()
                .id("msg-123")
                .type(Message.Type.MESSAGE)
                .role(Message.Role.ASSISTANT)
                .content(List.of(ContentBlock.ofText(textBlock)))
                .model("claude-opus-4-5-20251101")
                .stopReason(StopReason.END_TURN)
                .usage(Usage.builder()
                        .inputTokens(10L)
                        .outputTokens(20L)
                        .build())
                .build();
    }

    private Message createToolUseResponse(String toolName, String toolId, Map<String, Object> inputs) {
        ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                .id(toolId)
                .name(toolName)
                .input(JsonValue.from(inputs))
                .build();

        return Message.builder()
                .id("msg-123")
                .type(Message.Type.MESSAGE)
                .role(Message.Role.ASSISTANT)
                .content(List.of(ContentBlock.ofToolUse(toolUseBlock)))
                .model("claude-opus-4-5-20251101")
                .stopReason(StopReason.TOOL_USE)
                .usage(Usage.builder()
                        .inputTokens(10L)
                        .outputTokens(20L)
                        .build())
                .build();
    }
}
