package com.redgreenrefactor.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.redgreenrefactor.model.AgentConfig;
import com.redgreenrefactor.tool.ToolDispatcher;
import com.redgreenrefactor.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Handles invoking TDD agents via the Anthropic API.
 *
 * <p>This class manages the conversation loop with the agent, including:
 * <ul>
 *   <li>Sending the initial prompt to the agent</li>
 *   <li>Handling tool use requests from the agent</li>
 *   <li>Executing tools and returning results</li>
 *   <li>Continuing the conversation until the agent completes</li>
 * </ul>
 */
public class AgentInvoker {

    private static final Logger LOG = LoggerFactory.getLogger(AgentInvoker.class);
    private static final long DEFAULT_MAX_TOKENS = 4096L;
    private static final int MAX_TOOL_USE_ITERATIONS = 50;

    private final AnthropicClient client;
    private final ToolDispatcher toolDispatcher;

    /**
     * Creates a new AgentInvoker.
     *
     * @param client         the Anthropic API client
     * @param toolDispatcher the tool dispatcher for executing tool calls
     */
    public AgentInvoker(AnthropicClient client, ToolDispatcher toolDispatcher) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.toolDispatcher = Objects.requireNonNull(toolDispatcher, "toolDispatcher cannot be null");
    }

    /**
     * Invokes an agent with the given prompt and returns the final response.
     *
     * <p>This method handles the full conversation loop, executing any tool
     * calls the agent makes until it returns a final response.
     *
     * @param config the agent configuration
     * @param prompt the user prompt to send to the agent
     * @return the result of the agent invocation
     * @throws AgentInvocationException if the agent fails to complete
     */
    public AgentResponse invoke(AgentConfig config, String prompt) {
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(prompt, "prompt cannot be null");

        LOG.info("Invoking agent: {} with prompt length: {}", config.name(), prompt.length());

        List<MessageParam> conversationHistory = new ArrayList<>();
        conversationHistory.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(MessageParam.Content.ofString(prompt))
                .build());

        int iteration = 0;
        Message response;

        while (iteration < MAX_TOOL_USE_ITERATIONS) {
            iteration++;
            LOG.debug("Agent {} iteration {}", config.name(), iteration);

            MessageCreateParams params = buildMessageParams(config, conversationHistory);
            response = client.messages().create(params);

            LOG.debug("Agent {} response stop reason: {}", config.name(), response.stopReason());

            // Add the assistant's response to the conversation history
            conversationHistory.add(MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)
                    .content(convertContentBlocksToParams(response.content()))
                    .build());

            // Check if we're done
            if (response.stopReason().equals(StopReason.END_TURN)) {
                String finalText = extractTextContent(response.content());
                LOG.info("Agent {} completed after {} iterations", config.name(), iteration);
                return new AgentResponse(finalText, iteration, conversationHistory);
            }

            // Handle tool use
            if (response.stopReason().equals(StopReason.TOOL_USE)) {
                List<ContentBlockParam> toolResults = executeToolCalls(response.content());
                if (!toolResults.isEmpty()) {
                    conversationHistory.add(MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .content(toolResults)
                            .build());
                }
            } else {
                // Unexpected stop reason
                LOG.warn("Agent {} stopped with unexpected reason: {}", config.name(), response.stopReason());
                String finalText = extractTextContent(response.content());
                return new AgentResponse(finalText, iteration, conversationHistory);
            }
        }

        throw new AgentInvocationException(
                "Agent " + config.name() + " exceeded maximum iterations (" + MAX_TOOL_USE_ITERATIONS + ")");
    }

    private MessageCreateParams buildMessageParams(AgentConfig config, List<MessageParam> history) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(DEFAULT_MAX_TOKENS)
                .system(config.systemPrompt())
                .messages(history);

        // Add tools if configured
        if (!config.tools().isEmpty()) {
            builder.tools(config.tools());
        }

        return builder.build();
    }

    private List<ContentBlockParam> executeToolCalls(List<ContentBlock> contentBlocks) {
        List<ContentBlockParam> results = new ArrayList<>();

        for (ContentBlock block : contentBlocks) {
            if (block.isToolUse()) {
                ToolUseBlock toolUse = block.toolUse();
                LOG.debug("Executing tool: {} with id: {}", toolUse.name(), toolUse.id());

                try {
                    Map<String, Object> inputs = convertJsonValueToMap(toolUse.input());
                    ToolResult result = toolDispatcher.dispatch(toolUse.name(), inputs);
                    boolean isError = !result.success();
                    results.add(ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                    .toolUseId(toolUse.id())
                                    .content(result.getContent())
                                    .isError(isError)
                                    .build()));
                    LOG.debug("Tool {} completed, isError: {}", toolUse.name(), isError);
                } catch (Exception e) {
                    LOG.error("Tool {} failed with exception", toolUse.name(), e);
                    results.add(ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                    .toolUseId(toolUse.id())
                                    .content("Error executing tool: " + e.getMessage())
                                    .isError(true)
                                    .build()));
                }
            }
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertJsonValueToMap(JsonValue jsonValue) {
        return jsonValue.accept(new JsonValue.Visitor<>() {
            @Override
            public Map<String, Object> visitNull() {
                return new HashMap<>();
            }

            @Override
            public Map<String, Object> visitBoolean(boolean value) {
                return new HashMap<>();
            }

            @Override
            public Map<String, Object> visitNumber(Number value) {
                return new HashMap<>();
            }

            @Override
            public Map<String, Object> visitString(String value) {
                return new HashMap<>();
            }

            @Override
            public Map<String, Object> visitArray(List<JsonValue> values) {
                return new HashMap<>();
            }

            @Override
            public Map<String, Object> visitObject(Map<String, JsonValue> values) {
                Map<String, Object> result = new HashMap<>();
                for (Map.Entry<String, JsonValue> entry : values.entrySet()) {
                    result.put(entry.getKey(), convertJsonValueToObject(entry.getValue()));
                }
                return result;
            }
        });
    }

    private Object convertJsonValueToObject(JsonValue jsonValue) {
        return jsonValue.accept(new JsonValue.Visitor<>() {
            @Override
            public Object visitNull() {
                return null;
            }

            @Override
            public Object visitBoolean(boolean value) {
                return value;
            }

            @Override
            public Object visitNumber(Number value) {
                return value;
            }

            @Override
            public Object visitString(String value) {
                return value;
            }

            @Override
            public Object visitArray(List<JsonValue> values) {
                List<Object> result = new ArrayList<>();
                for (JsonValue v : values) {
                    result.add(convertJsonValueToObject(v));
                }
                return result;
            }

            @Override
            public Object visitObject(Map<String, JsonValue> values) {
                Map<String, Object> result = new HashMap<>();
                for (Map.Entry<String, JsonValue> entry : values.entrySet()) {
                    result.put(entry.getKey(), convertJsonValueToObject(entry.getValue()));
                }
                return result;
            }
        });
    }

    private String extractTextContent(List<ContentBlock> contentBlocks) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : contentBlocks) {
            if (block.isText()) {
                TextBlock textBlock = block.text();
                if (text.length() > 0) {
                    text.append("\n");
                }
                text.append(textBlock.text());
            }
        }
        return text.toString();
    }

    private List<ContentBlockParam> convertContentBlocksToParams(List<ContentBlock> blocks) {
        List<ContentBlockParam> params = new ArrayList<>();
        for (ContentBlock block : blocks) {
            if (block.isText()) {
                params.add(ContentBlockParam.ofText(
                        TextBlockParam.builder()
                                .text(block.text().text())
                                .build()));
            } else if (block.isToolUse()) {
                ToolUseBlock toolUse = block.toolUse();
                params.add(ContentBlockParam.ofToolUse(
                        com.anthropic.models.messages.ToolUseBlockParam.builder()
                                .id(toolUse.id())
                                .name(toolUse.name())
                                .input(toolUse.input())
                                .build()));
            }
        }
        return params;
    }

    /**
     * Result of an agent invocation.
     *
     * @param responseText       the final text response from the agent
     * @param iterationCount     the number of conversation iterations
     * @param conversationHistory the full conversation history
     */
    public record AgentResponse(
            String responseText,
            int iterationCount,
            List<MessageParam> conversationHistory
    ) {
        public AgentResponse {
            Objects.requireNonNull(responseText, "responseText cannot be null");
            Objects.requireNonNull(conversationHistory, "conversationHistory cannot be null");
            conversationHistory = List.copyOf(conversationHistory);
        }
    }
}
