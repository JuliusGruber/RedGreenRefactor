# Claude Java SDK for Agent Orchestration: Photon Summary

> **Verified**: The orchestration layer must be built manually for Java. The Python Agent SDK has built-in orchestration; the Java SDK does not.

## Key Difference: Python vs Java

| Capability | Python Agent SDK | Java SDK |
|------------|-----------------|----------|
| AgentDefinition | Built-in class | Manual: `record AgentDefinition(...)` |
| Agent invocation | Built-in `query()` | Manual: message creation + tool loop |
| Subagents (Task tool) | Built-in | Manual orchestration pattern |
| Session tracking | Built-in `session_id` | Manual state management |
| Tool decorators | `@tool` decorator | `Tool.builder()` + JSON Schema |

## Java SDK: What You Get

```xml
<dependency>
    <groupId>com.anthropic</groupId>
    <artifactId>anthropic-java</artifactId>
    <version>2.11.1</version>
</dependency>
```

**Core API features available:**
- `AnthropicClient` / `AnthropicOkHttpClient` - API access
- `MessageCreateParams.builder()` - Request construction
- `Tool.builder()` - Tool definitions via JSON Schema
- `StreamResponse<RawMessageStreamEvent>` - Streaming
- `CompletableFuture<Message>` - Async execution

## What You Must Build

1. **Agent Definitions** - Custom record class with name, prompt, tools, model
2. **Orchestration Loop** - Message creation → tool use detection → tool execution → continue
3. **State Management** - `.tdd-state.json` or equivalent for inter-agent coordination
4. **Tool Execution** - Implement handlers for each tool (read, write, bash, etc.)

## Minimal Orchestrator Pattern

```java
public class Orchestrator {
    private final AnthropicClient client = AnthropicOkHttpClient.fromEnv();

    public Message runAgent(String systemPrompt, String userPrompt, List<Tool> tools) {
        Message response = client.messages().create(
            MessageCreateParams.builder()
                .model(Model.CLAUDE_SONNET_4_20250514)
                .maxTokens(4096L)
                .system(systemPrompt)
                .addUserMessage(userPrompt)
                .tools(tools)
                .build());

        // Tool loop - keep going until no more tool calls
        while (response.stopReason() == StopReason.TOOL_USE) {
            List<ToolResultBlockParam> results = executeTools(response);
            response = continueWithResults(response, results);
        }
        return response;
    }
}
```

## State File Pattern (Handoffs)

```json
{
  "current_phase": "green",
  "current_test": { "description": "...", "test_file": "...", "impl_file": "..." },
  "pending_tests": ["..."],
  "completed_tests": ["..."]
}
```

Each agent reads/writes this file. No implicit state sharing exists.

## Tool Definition Pattern

```java
Tool.builder()
    .name("read_file")
    .description("Read file contents")
    .inputSchema(Tool.InputSchema.builder()
        .type(JsonValue.Type.OBJECT)
        .putAdditionalProperty("properties", JsonValue.from(Map.of(
            "file_path", Map.of("type", "string"))))
        .putAdditionalProperty("required", JsonValue.from(List.of("file_path")))
        .build())
    .build();
```

## Bottom Line

- **Python Agent SDK**: Import → define agents → orchestration works
- **Java SDK**: Import → build orchestrator → implement tool loop → manage state manually

The Java SDK is **fully capable** but requires ~200-400 lines of orchestration code that Python gets for free.
