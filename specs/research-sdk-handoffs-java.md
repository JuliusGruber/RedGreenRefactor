# Anthropic Java SDK for Handoffs: In-Depth Research

> **Purpose**: Evaluate how the Anthropic Java SDK can be used to implement handoffs between the four TDD agents in the RedGreenRefactor workflow.

## Executive Summary

The Anthropic Java SDK (`anthropic-java`) provides the foundational API access for building multi-agent orchestration. Unlike the Python Claude Agent SDK which has built-in agent orchestration primitives, the Java SDK requires building the orchestration layer manually using the core API features: **message creation**, **tool use**, **streaming**, and **async execution**.

**Key findings:**
- The Java SDK provides core API access but requires manual orchestration logic
- Tool use via JSON schemas enables agent-specific capabilities
- State must be explicitly passed via filesystem, prompts, or custom state management
- Both synchronous and asynchronous execution patterns are supported
- Streaming is available for long-running agent interactions

---

## Feature Verification: Java SDK Capabilities

The following table confirms which TDD workflow features are supported by the Anthropic Java SDK:

| Feature | Supported | Implementation Notes |
|---------|-----------|---------------------|
| **Message Creation** | ✅ Yes | `MessageCreateParams.builder()` with model, maxTokens, messages |
| **System Prompts** | ✅ Yes | `.system(String)` in MessageCreateParams |
| **Tool Definitions** | ✅ Yes | `Tool.builder()` with JSON Schema input definition |
| **Tool Use Responses** | ✅ Yes | `StopReason.TOOL_USE` detection, `ToolUseBlock` parsing |
| **Tool Results** | ✅ Yes | `ToolResultBlockParam` for returning tool execution results |
| **Streaming** | ✅ Yes | `StreamResponse<RawMessageStreamEvent>` with auto-close |
| **Async Execution** | ✅ Yes | `CompletableFuture<Message>` via `.async()` |
| **Model Selection** | ✅ Yes | `Model.CLAUDE_SONNET_4_20250514` enum |
| **Conversation History** | ✅ Yes | List of `MessageParam` for multi-turn conversations |
| **Beta Features** | ✅ Yes | `BetaTool`, `BetaMessage` for experimental features |

### Features Requiring Manual Implementation

Unlike the Python Claude Agent SDK, the following require custom code:

| Feature | Python SDK | Java SDK Equivalent |
|---------|------------|---------------------|
| `AgentDefinition` class | Built-in | Custom record class (shown above) |
| `query()` function | Built-in | Manual message creation + tool loop |
| Session ID tracking | Built-in | Manual state management |
| Task tool (subagents) | Built-in | Manual orchestration pattern |
| MCP tool decorators | `@tool` decorator | `Tool.builder()` with JSON Schema |
| Async iteration | `async for msg in query()` | `CompletableFuture` chains |

**Conclusion**: All required functionality for the TDD workflow IS possible with the Java SDK, but requires more manual orchestration code compared to the Python SDK.

---

## 1. SDK Architecture Overview

### 1.1 Core Components

| Component | Description | Relevance to TDD Workflow |
|-----------|-------------|---------------------------|
| **AnthropicClient** | Main client for API calls | Core interface for all agent invocations |
| **MessageCreateParams** | Request configuration | Define agent prompts, tools, and models |
| **Tool** / **BetaTool** | Tool definitions | Agent-specific tool restrictions |
| **StreamResponse** | Streaming responses | Long-running agent interactions |
| **CompletableFuture** | Async execution | Non-blocking agent coordination |

### 1.2 Maven/Gradle Dependencies

```xml
<!-- Maven -->
<dependency>
    <groupId>com.anthropic</groupId>
    <artifactId>anthropic-java</artifactId>
    <version>2.11.1</version>
</dependency>
```

```kotlin
// Gradle
implementation("com.anthropic:anthropic-java:2.11.1")
```

**Requirements:** Java 8 or later

### 1.3 Two Session Models

```
┌────────────────────────────────────────────────────────────────────┐
│                    SESSION MODELS COMPARISON                        │
├────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  OPTION A: Independent Sessions (Matches spec requirement)          │
│  ─────────────────────────────────────────────────────────────────  │
│                                                                     │
│    Session 1          Session 2          Session 3          Session 4
│    ┌─────────┐        ┌─────────┐        ┌─────────┐        ┌─────────┐
│    │ Test    │   →    │ Test    │   →    │ Impl    │   →    │ Refactor│
│    │ List    │ files  │ Agent   │ files  │ Agent   │ files  │ Agent   │
│    │ Agent   │        │         │        │         │        │         │
│    └─────────┘        └─────────┘        └─────────┘        └─────────┘
│         ↓                  ↓                  ↓                  ↓
│    Fresh context      Fresh context      Fresh context      Fresh context
│                                                                     │
│  OPTION B: Conversation Continuity (Context Accumulation)           │
│  ─────────────────────────────────────────────────────────────────  │
│                                                                     │
│    Accumulated message history                                      │
│    ┌─────────────────────────────────────────────────────────────┐  │
│    │ Turn 1: Test List │ Turn 2: Test │ Turn 3: Impl │ Turn 4: Ref│ │
│    │ Agent creates     │ Agent writes │ Agent makes  │ Actor      │  │
│    │ test list         │ failing test │ test pass    │ refactors  │  │
│    └─────────────────────────────────────────────────────────────┘  │
│         ↓                  ↓                  ↓                  ↓
│    Context grows: 100% → 150% → 200% → 250% of original             │
│                                                                     │
└────────────────────────────────────────────────────────────────────┘
```

**Recommendation**: Use **Option A (Independent Sessions)** to match the spec requirement ("New session each time"), with explicit context passing via filesystem.

---

## 2. Agent Architecture for TDD

### 2.1 Defining the Four Agents

The Java SDK requires programmatic agent definitions using builder patterns:

```java
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;

public class TDDAgentConfig {

    // Shared client instance (reuse across agents)
    private final AnthropicClient client;

    public TDDAgentConfig() {
        this.client = AnthropicOkHttpClient.fromEnv();
    }

    /**
     * Agent configuration record containing prompt and tool restrictions
     */
    public record AgentDefinition(
        String name,
        String description,
        String systemPrompt,
        List<Tool> allowedTools,
        Model model
    ) {}

    public AgentDefinition testListAgent() {
        return new AgentDefinition(
            "test-list-agent",
            "Planning specialist that creates and maintains the test list",
            """
            You are the Test List Agent in a TDD workflow.

            Your responsibilities:
            1. Analyze feature requirements
            2. Create/update the test list file
            3. Select the next pending test
            4. Determine when the feature is complete

            You DO NOT write actual test code—only plan tests.

            Always read and update the .tdd-state.json file to coordinate with other agents.
            """,
            List.of(createReadTool(), createWriteTool(), createGlobTool(), createGrepTool()),
            Model.CLAUDE_SONNET_4_20250514
        );
    }

    public AgentDefinition testAgent() {
        return new AgentDefinition(
            "test-agent",
            "Red phase specialist that writes failing tests",
            """
            You are the Test Agent (Red Phase) in a TDD workflow.

            Your responsibilities:
            1. Receive ONE test case description from .tdd-state.json
            2. Write a failing test for that case
            3. Verify the test fails (all other tests pass)
            4. Commit the failing test

            Write minimal, focused tests.
            """,
            List.of(createReadTool(), createWriteTool(), createBashTool()),
            Model.CLAUDE_SONNET_4_20250514
        );
    }

    public AgentDefinition implementingAgent() {
        return new AgentDefinition(
            "implementing-agent",
            "Green phase specialist that makes tests pass",
            """
            You are the Implementing Agent (Green Phase) in a TDD workflow.

            Your responsibilities:
            1. Read the failing test
            2. Write MINIMUM code to make it pass
            3. Ensure all tests pass
            4. Commit the implementation

            Do NOT over-engineer. Write just enough code.
            """,
            List.of(createReadTool(), createWriteTool(), createEditTool(), createBashTool()),
            Model.CLAUDE_SONNET_4_20250514
        );
    }

    public AgentDefinition refactorAgent() {
        return new AgentDefinition(
            "refactor-agent",
            "Refactor phase specialist that improves code quality",
            """
            You are the Refactor Agent in a TDD workflow.

            Your responsibilities:
            1. Review implementation AND test code
            2. Refactor for clarity, maintainability
            3. Ensure all tests still pass
            4. Commit refactored code

            May refactor any code in the codebase.
            """,
            List.of(createReadTool(), createEditTool(), createBashTool(), createGrepTool()),
            Model.CLAUDE_SONNET_4_20250514
        );
    }
}
```

### 2.2 Tool Definitions via JSON Schema

Each agent receives only the tools needed for its phase:

```java
import com.anthropic.models.messages.Tool;
import com.anthropic.core.JsonValue;

public class TDDTools {

    /**
     * Create a tool definition with JSON schema for input
     */
    public static Tool createReadTool() {
        return Tool.builder()
            .name("read_file")
            .description("Read the contents of a file")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.Type.OBJECT)
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                    "file_path", Map.of(
                        "type", "string",
                        "description", "The absolute path to the file to read"
                    )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("file_path")))
                .build())
            .build();
    }

    public static Tool createWriteTool() {
        return Tool.builder()
            .name("write_file")
            .description("Write content to a file")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.Type.OBJECT)
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                    "file_path", Map.of(
                        "type", "string",
                        "description", "The absolute path to the file"
                    ),
                    "content", Map.of(
                        "type", "string",
                        "description", "The content to write"
                    )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("file_path", "content")))
                .build())
            .build();
    }

    public static Tool createEditTool() {
        return Tool.builder()
            .name("edit_file")
            .description("Edit a file by replacing text")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.Type.OBJECT)
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                    "file_path", Map.of(
                        "type", "string",
                        "description", "The absolute path to the file"
                    ),
                    "old_text", Map.of(
                        "type", "string",
                        "description", "The text to replace"
                    ),
                    "new_text", Map.of(
                        "type", "string",
                        "description", "The replacement text"
                    )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("file_path", "old_text", "new_text")))
                .build())
            .build();
    }

    public static Tool createBashTool() {
        return Tool.builder()
            .name("bash")
            .description("Execute a bash command")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.Type.OBJECT)
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                    "command", Map.of(
                        "type", "string",
                        "description", "The bash command to execute"
                    )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("command")))
                .build())
            .build();
    }

    public static Tool createGlobTool() {
        return Tool.builder()
            .name("glob")
            .description("Find files matching a glob pattern")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.Type.OBJECT)
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                    "pattern", Map.of(
                        "type", "string",
                        "description", "The glob pattern to match"
                    )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("pattern")))
                .build())
            .build();
    }

    public static Tool createGrepTool() {
        return Tool.builder()
            .name("grep")
            .description("Search for text patterns in files")
            .inputSchema(Tool.InputSchema.builder()
                .type(JsonValue.Type.OBJECT)
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                    "pattern", Map.of(
                        "type", "string",
                        "description", "The regex pattern to search for"
                    ),
                    "path", Map.of(
                        "type", "string",
                        "description", "The directory or file to search in"
                    )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of("pattern")))
                .build())
            .build();
    }
}
```

### 2.3 Agent Tool Restrictions Matrix

| Agent | read_file | write_file | edit_file | bash | glob | grep |
|-------|-----------|------------|-----------|------|------|------|
| Test List | ✓ | ✓ | | | ✓ | ✓ |
| Test | ✓ | ✓ | | ✓ | | |
| Implementing | ✓ | ✓ | ✓ | ✓ | | |
| Refactor | ✓ | | ✓ | ✓ | | ✓ |

**Rationale:**
- **Test List Agent**: Plans only, no code execution
- **Test Agent**: Writes new files (tests), runs tests to verify failure
- **Implementing Agent**: Can create new files OR edit existing ones
- **Refactor Agent**: Edit only (no creating new files), can search for patterns

---

## 3. Chosen Handoff Approach

> **Decision**: The handoff mechanism uses **Anthropic SDK + Git Commits + Git Notes**.
>
> See [spec-handoffs.md](spec-handoffs.md) for the full specification of the chosen approach.
> See [research-java-sdk-git-notes.md](research-java-sdk-git-notes.md) for detailed Java implementation with Git Notes.

---

## 4. Implementation Patterns

> **Note**: The orchestrator examples below reference `.tdd-state.json` for illustration. The actual implementation uses **Git Notes** for state management. See [research-java-sdk-git-notes.md](research-java-sdk-git-notes.md) for the authoritative Git Notes implementation.

### 4.1 Java Orchestrator (Full Example)

```java
package com.redgreenrefactor;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * TDD Workflow Orchestrator using Anthropic Java SDK
 */
public class TDDOrchestrator {

    private final AnthropicClient client;
    private final Path projectRoot;
    private final Path stateFile;
    private final ObjectMapper mapper;
    private final TDDAgentConfig agentConfig;

    public TDDOrchestrator(Path projectRoot) {
        this.client = AnthropicOkHttpClient.fromEnv();
        this.projectRoot = projectRoot;
        this.stateFile = projectRoot.resolve(".tdd-state.json");
        this.mapper = new ObjectMapper();
        this.agentConfig = new TDDAgentConfig();
    }

    /**
     * Run a single agent phase with the given configuration
     */
    public Message runAgent(TDDAgentConfig.AgentDefinition agent, String userPrompt) {
        MessageCreateParams params = MessageCreateParams.builder()
            .model(agent.model())
            .maxTokens(4096L)
            .system(agent.systemPrompt())
            .addUserMessage(userPrompt)
            .tools(agent.allowedTools())
            .build();

        Message response = client.messages().create(params);

        // Handle tool use in a loop until completion
        while (response.stopReason() == StopReason.TOOL_USE) {
            List<MessageParam> messages = new ArrayList<>();
            messages.add(MessageParam.ofAssistant(response.content()));

            // Process each tool use block
            List<ToolResultBlockParam> toolResults = new ArrayList<>();
            for (ContentBlock block : response.content()) {
                if (block.isToolUse()) {
                    ToolUseBlock toolUse = block.toolUse();
                    String result = executeToolCall(toolUse);
                    toolResults.add(ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content(result)
                        .build());
                }
            }

            messages.add(MessageParam.ofUser(
                toolResults.stream()
                    .map(ContentBlockParam::ofToolResult)
                    .toList()
            ));

            // Continue conversation with tool results
            params = MessageCreateParams.builder()
                .model(agent.model())
                .maxTokens(4096L)
                .system(agent.systemPrompt())
                .messages(messages)
                .tools(agent.allowedTools())
                .build();

            response = client.messages().create(params);
        }

        return response;
    }

    /**
     * Execute a tool call and return the result
     */
    private String executeToolCall(ToolUseBlock toolUse) {
        String toolName = toolUse.name();
        var input = toolUse.input();

        return switch (toolName) {
            case "read_file" -> executeReadFile(input);
            case "write_file" -> executeWriteFile(input);
            case "edit_file" -> executeEditFile(input);
            case "bash" -> executeBash(input);
            case "glob" -> executeGlob(input);
            case "grep" -> executeGrep(input);
            default -> "Unknown tool: " + toolName;
        };
    }

    private String executeReadFile(Object input) {
        try {
            var params = mapper.convertValue(input, ReadFileParams.class);
            Path filePath = Path.of(params.filePath());
            return Files.readString(filePath);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    private String executeWriteFile(Object input) {
        try {
            var params = mapper.convertValue(input, WriteFileParams.class);
            Path filePath = Path.of(params.filePath());
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, params.content());
            return "File written successfully: " + params.filePath();
        } catch (Exception e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    private String executeEditFile(Object input) {
        try {
            var params = mapper.convertValue(input, EditFileParams.class);
            Path filePath = Path.of(params.filePath());
            String content = Files.readString(filePath);
            content = content.replace(params.oldText(), params.newText());
            Files.writeString(filePath, content);
            return "File edited successfully: " + params.filePath();
        } catch (Exception e) {
            return "Error editing file: " + e.getMessage();
        }
    }

    private String executeBash(Object input) {
        try {
            var params = mapper.convertValue(input, BashParams.class);
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", params.command());
            pb.directory(projectRoot.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            return "Exit code: " + exitCode + "\n" + output;
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    private String executeGlob(Object input) {
        try {
            var params = mapper.convertValue(input, GlobParams.class);
            var matcher = projectRoot.getFileSystem().getPathMatcher("glob:" + params.pattern());
            List<String> matches = new ArrayList<>();
            Files.walk(projectRoot)
                .filter(p -> matcher.matches(projectRoot.relativize(p)))
                .forEach(p -> matches.add(p.toString()));
            return String.join("\n", matches);
        } catch (Exception e) {
            return "Error globbing: " + e.getMessage();
        }
    }

    private String executeGrep(Object input) {
        try {
            var params = mapper.convertValue(input, GrepParams.class);
            ProcessBuilder pb = new ProcessBuilder(
                "grep", "-r", "-n", params.pattern(),
                params.path() != null ? params.path() : "."
            );
            pb.directory(projectRoot.toFile());
            Process process = pb.start();
            return new String(process.getInputStream().readAllBytes());
        } catch (Exception e) {
            return "Error grepping: " + e.getMessage();
        }
    }

    // Parameter record classes
    record ReadFileParams(String filePath) {}
    record WriteFileParams(String filePath, String content) {}
    record EditFileParams(String filePath, String oldText, String newText) {}
    record BashParams(String command) {}
    record GlobParams(String pattern) {}
    record GrepParams(String pattern, String path) {}

    /**
     * Run Phase 1: Test List Agent (Planning)
     */
    public void runTestListAgent(String featureRequest) {
        System.out.println("📋 Phase 1: Planning (Test List Agent)");

        TDDState state = TDDState.load(stateFile);
        String prompt = String.format("""
            Feature Request: %s

            Current State: %s

            Tasks:
            1. Read any existing test list
            2. Create or update the test list
            3. Select the next pending test
            4. Update .tdd-state.json with the next test

            Output the selected test description clearly.
            """, featureRequest, state.toJson());

        runAgent(agentConfig.testListAgent(), prompt);
    }

    /**
     * Run Phase 2: Test Agent (Red Phase)
     */
    public void runTestAgent() {
        System.out.println("🔴 Phase 2: Red (Test Agent)");

        TDDState state = TDDState.load(stateFile);
        String prompt = String.format("""
            Current Test: %s

            State: %s

            Tasks:
            1. Write a failing test for the current test case
            2. Run tests to verify your new test fails
            3. Commit the failing test
            4. Update .tdd-state.json with phase completion
            """, state.getCurrentTest().description(), state.toJson());

        runAgent(agentConfig.testAgent(), prompt);
    }

    /**
     * Run Phase 3: Implementing Agent (Green Phase)
     */
    public void runImplementingAgent() {
        System.out.println("🟢 Phase 3: Green (Implementing Agent)");

        TDDState state = TDDState.load(stateFile);
        String prompt = String.format("""
            Current Test: %s

            State: %s

            Tasks:
            1. Read the failing test
            2. Write MINIMUM code to make it pass
            3. Run all tests to verify they pass
            4. Commit the implementation
            5. Update .tdd-state.json
            """, state.getCurrentTest(), state.toJson());

        runAgent(agentConfig.implementingAgent(), prompt);
    }

    /**
     * Run Phase 4: Refactor Agent
     */
    public void runRefactorAgent() {
        System.out.println("🔵 Phase 4: Refactor (Refactor Agent)");

        TDDState state = TDDState.load(stateFile);
        String prompt = String.format("""
            Current Test: %s

            State: %s

            Tasks:
            1. Review the test and implementation
            2. Refactor for clarity and maintainability
            3. Run tests to ensure they still pass
            4. Commit refactored code
            5. Update .tdd-state.json to complete cycle
            """, state.getCurrentTest(), state.toJson());

        runAgent(agentConfig.refactorAgent(), prompt);
    }

    /**
     * Run one complete TDD cycle
     */
    public void runCycle(String featureRequest) {
        runTestListAgent(featureRequest);
        runTestAgent();
        runImplementingAgent();
        runRefactorAgent();
        System.out.println("✅ Cycle complete!");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java TDDOrchestrator <project_root> <feature_request>");
            System.exit(1);
        }

        TDDOrchestrator orchestrator = new TDDOrchestrator(Path.of(args[0]));
        orchestrator.runCycle(args[1]);
    }
}
```

### 4.2 Asynchronous Orchestrator Pattern

For non-blocking execution with better resource utilization:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncTDDOrchestrator {

    private final AnthropicClient client;
    private final ExecutorService executor;

    public AsyncTDDOrchestrator() {
        this.client = AnthropicOkHttpClient.fromEnv();
        this.executor = Executors.newFixedThreadPool(4);
    }

    /**
     * Run agent asynchronously
     */
    public CompletableFuture<Message> runAgentAsync(
            TDDAgentConfig.AgentDefinition agent,
            String userPrompt) {

        return CompletableFuture.supplyAsync(() -> {
            MessageCreateParams params = MessageCreateParams.builder()
                .model(agent.model())
                .maxTokens(4096L)
                .system(agent.systemPrompt())
                .addUserMessage(userPrompt)
                .tools(agent.allowedTools())
                .build();

            return client.messages().create(params);
        }, executor);
    }

    /**
     * Run TDD cycle with async/await pattern
     */
    public CompletableFuture<Void> runCycleAsync(String featureRequest) {
        return runTestListAgentAsync(featureRequest)
            .thenCompose(v -> runTestAgentAsync())
            .thenCompose(v -> runImplementingAgentAsync())
            .thenCompose(v -> runRefactorAgentAsync())
            .thenRun(() -> System.out.println("✅ Cycle complete!"));
    }

    public void shutdown() {
        executor.shutdown();
    }
}
```

### 4.3 Streaming for Long-Running Agents

For agents that may take longer to complete:

```java
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.RawMessageStreamEvent;

public class StreamingAgentRunner {

    private final AnthropicClient client;

    public StreamingAgentRunner() {
        this.client = AnthropicOkHttpClient.fromEnv();
    }

    /**
     * Run agent with streaming output
     */
    public void runAgentWithStreaming(
            TDDAgentConfig.AgentDefinition agent,
            String userPrompt,
            Consumer<String> onTextDelta) {

        MessageCreateParams params = MessageCreateParams.builder()
            .model(agent.model())
            .maxTokens(4096L)
            .system(agent.systemPrompt())
            .addUserMessage(userPrompt)
            .tools(agent.allowedTools())
            .build();

        try (StreamResponse<RawMessageStreamEvent> stream =
                client.messages().createStreaming(params)) {

            stream.stream()
                .flatMap(event -> event.contentBlockDelta().stream())
                .flatMap(delta -> delta.delta().text().stream())
                .forEach(textDelta -> onTextDelta.accept(textDelta.text()));
        }
    }

    /**
     * Run with MessageAccumulator for complete response
     */
    public Message runAgentWithAccumulator(
            TDDAgentConfig.AgentDefinition agent,
            String userPrompt) {

        MessageCreateParams params = MessageCreateParams.builder()
            .model(agent.model())
            .maxTokens(4096L)
            .system(agent.systemPrompt())
            .addUserMessage(userPrompt)
            .tools(agent.allowedTools())
            .build();

        MessageAccumulator accumulator = MessageAccumulator.create();

        try (StreamResponse<RawMessageStreamEvent> stream =
                client.messages().createStreaming(params)) {

            stream.stream().forEach(accumulator::accumulate);
        }

        return accumulator.message();
    }
}
```

---

## 5. Error Handling

### 5.1 Detecting Failures

```java
public class TDDErrorHandler {

    /**
     * Check if a bash tool result indicates test failure
     */
    public boolean isTestFailure(String bashOutput) {
        return bashOutput.contains("FAILED") ||
               bashOutput.contains("FAIL:") ||
               bashOutput.contains("Error") ||
               bashOutput.matches(".*Exit code: [^0].*");
    }

    /**
     * Check if test passes when it should fail (Red phase issue)
     */
    public boolean isUnexpectedPass(String bashOutput, String phase) {
        if ("red".equals(phase)) {
            return bashOutput.contains("OK") ||
                   bashOutput.contains("passed") ||
                   bashOutput.matches(".*Exit code: 0.*");
        }
        return false;
    }

    /**
     * Extract error details from output
     */
    public List<String> extractErrors(String output) {
        List<String> errors = new ArrayList<>();
        for (String line : output.split("\n")) {
            if (line.contains("Error") ||
                line.contains("Exception") ||
                line.contains("FAIL")) {
                errors.add(line.trim());
            }
        }
        return errors;
    }
}
```

### 5.2 Retry with Exponential Backoff

```java
public class RetryHandler {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 1000;

    public <T> T executeWithRetry(Supplier<T> operation) throws Exception {
        Exception lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;

                if (attempt < MAX_RETRIES - 1) {
                    long delay = INITIAL_DELAY_MS * (long) Math.pow(2, attempt);
                    System.err.printf("Attempt %d failed, retrying in %dms: %s%n",
                        attempt + 1, delay, e.getMessage());
                    Thread.sleep(delay);
                }
            }
        }

        throw lastException;
    }

    /**
     * Run agent phase with retry logic
     */
    public Message runAgentWithRetry(
            TDDOrchestrator orchestrator,
            TDDAgentConfig.AgentDefinition agent,
            String prompt) throws Exception {

        return executeWithRetry(() -> orchestrator.runAgent(agent, prompt));
    }
}
```

### 5.3 Recovery Strategies

| Failure Type | Java Recovery Approach |
|--------------|----------------------|
| Test doesn't compile | Re-run test-agent with error context in prompt |
| Test passes immediately | Re-run test-agent with instruction to check assertions |
| Implementation breaks other tests | Rollback via git, try alternative approach |
| Refactoring breaks tests | Rollback via git, re-run refactor-agent |
| API rate limit | Exponential backoff with retry |
| Network timeout | Retry with increased timeout |

---

## 6. Comparison: Java SDK vs Python SDK

| Feature | Java SDK (anthropic-java) | Python SDK (claude_agent_sdk) |
|---------|---------------------------|-------------------------------|
| **Agent Definitions** | Manual (builder pattern) | Built-in AgentDefinition class |
| **Tool Definitions** | JSON Schema via builders | Decorator-based |
| **Session Management** | Manual | Built-in session_id tracking |
| **Subagent Invocation** | Manual orchestration | Built-in Task tool |
| **Streaming** | StreamResponse + accumulator | async for pattern |
| **Async Support** | CompletableFuture | Native async/await |
| **Error Handling** | Try-catch | Exception + async error handling |
| **Type Safety** | Strong (records, generics) | Dynamic (type hints) |
| **Ecosystem** | Spring AI integration | Native Python tools |

---

## 7. Recommendations

### 7.1 For This Project (RedGreenRefactor)

**Chosen approach: Anthropic SDK + Git Commits + Git Notes**

1. **Use Anthropic Java SDK** for API calls to Claude
2. **Build orchestration layer** manually with JGit for Git Notes
3. **Use Git Notes** for state persistence and handoff data
4. **Use git commits** for atomic phase completion markers
5. **Use tool restrictions** via selective tool lists per agent

See [spec-handoffs.md](spec-handoffs.md) for the full specification.
See [research-java-sdk-git-notes.md](research-java-sdk-git-notes.md) for detailed Git Notes implementation.

### 7.2 Implementation Priority

1. **Start simple**: Basic orchestrator with synchronous execution
2. **Add state management**: Git Notes via JGit for coordination
3. **Add tool execution**: Implement each tool handler
4. **Add error handling**: Retry logic with error context
5. **Optimize later**: Streaming, async execution, parallel cycles

### 7.3 Key Success Factors

- **Clear handoff state schema**: Define Git Notes structure upfront
- **Explicit prompts**: Each agent gets full context in its prompt
- **Tool restrictions**: Only provide necessary tools per agent
- **Commit discipline**: Each phase commits before handoff
- **Error detection**: Parse test output to detect failures early
- **Immutable patterns**: Leverage Java record classes for state

---

## 8. Open Questions for Implementation

1. **Spring AI Integration**: Should we use Spring AI's AnthropicChatModel for better DI support?
2. **Parallel cycles**: Can multiple TDD cycles run in parallel using CompletableFuture?
3. **Human approval gates**: Should there be optional pause points for human review?
4. **Rollback granularity**: Should rollback be to last commit or last known-good state?
5. **Test framework detection**: Should orchestrator auto-detect JUnit/TestNG/etc.?
6. **Tool execution sandboxing**: How to safely execute bash commands?

---

## Appendix A: Required Imports

```java
// Anthropic SDK
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.*;

// Java standard library
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

// JSON processing
import com.fasterxml.jackson.databind.ObjectMapper;
```

## Appendix B: Maven POM Dependencies

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <dependencies>
        <!-- Anthropic Java SDK -->
        <dependency>
            <groupId>com.anthropic</groupId>
            <artifactId>anthropic-java</artifactId>
            <version>2.11.1</version>
        </dependency>

        <!-- Jackson for JSON processing -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.17.0</version>
        </dependency>

        <!-- Optional: Spring AI for enhanced integration -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-anthropic</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</project>
```

## Appendix C: Gradle Build Configuration

```kotlin
plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("com.anthropic:anthropic-java:2.11.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // Optional: Spring AI
    implementation("org.springframework.ai:spring-ai-anthropic:1.0.0")
}

application {
    mainClass.set("com.redgreenrefactor.TDDOrchestrator")
}
```
