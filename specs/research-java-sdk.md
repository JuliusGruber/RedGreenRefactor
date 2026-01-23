# Anthropic Java SDK for TDD Agent Handoffs

> **Purpose**: Comprehensive research on using the Anthropic Java SDK with Git Notes as a hybrid orchestration mechanism for the RedGreenRefactor TDD workflow.

## Executive Summary

This document consolidates research on building a multi-agent TDD orchestrator using:
1. **Anthropic Java SDK** - For invoking Claude agents with type-safe API calls
2. **Git Notes** - For non-intrusive metadata-based inter-agent communication
3. **JGit** - For programmatic Git Notes operations in Java

**Key findings:**
- The Java SDK provides core API access but requires manual orchestration logic (unlike Python SDK)
- Tool use via JSON schemas enables agent-specific capabilities
- Git Notes store handoff state without polluting commit history
- Both synchronous and asynchronous execution patterns are supported

---

## 1. Why Java + Git Notes?

### 1.1 Java SDK Advantages

| Advantage | Description |
|-----------|-------------|
| **Type Safety** | Compile-time checking prevents runtime errors in agent orchestration |
| **Enterprise Maturity** | Proven concurrency, error handling, and logging frameworks |
| **Build Tools** | Maven/Gradle for dependency management and reproducible builds |
| **IDE Support** | Excellent tooling in IntelliJ IDEA, Eclipse, VS Code |
| **Cross-Platform** | JVM runs identically on all major operating systems |
| **Async/Streaming** | `CompletableFuture` and streaming APIs for responsive agent interaction |

### 1.2 Git Notes Advantages

| Advantage | Description |
|-----------|-------------|
| **Non-Intrusive** | Attaches metadata without modifying commit SHA-1 hashes |
| **Version Controlled** | Notes are stored in `refs/notes/*`, fully tracked by Git |
| **Namespaced** | Different note types can be organized (e.g., `refs/notes/tdd-handoffs`) |
| **Shareable** | Can be pushed/pulled between repositories explicitly |
| **Human Readable** | Plain text or JSON, inspectable via command line |
| **No External Dependencies** | Uses only Git - no Redis, databases, or message queues |

### 1.3 Combined Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     HYBRID ARCHITECTURE OVERVIEW                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌────────────────────────────────────────────────────────────────────┐    │
│   │                    JAVA ORCHESTRATOR (JVM)                          │    │
│   │  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐            │    │
│   │  │ Anthropic    │   │ JGit         │   │ State        │            │    │
│   │  │ Java SDK     │   │ Library      │   │ Manager      │            │    │
│   │  │              │   │              │   │              │            │    │
│   │  │ - Messages   │   │ - Read Notes │   │ - Phase      │            │    │
│   │  │ - Streaming  │   │ - Write Notes│   │ - Cycle      │            │    │
│   │  │ - Tools      │   │ - Commits    │   │ - Errors     │            │    │
│   │  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘            │    │
│   │         │                  │                  │                     │    │
│   │         └──────────────────┴──────────────────┘                     │    │
│   │                            │                                        │    │
│   └────────────────────────────┼────────────────────────────────────────┘    │
│                                │                                             │
│                                ▼                                             │
│   ┌────────────────────────────────────────────────────────────────────┐    │
│   │                    GIT REPOSITORY                                   │    │
│   │                                                                     │    │
│   │   ┌─────────────────┐     ┌─────────────────────────────────────┐  │    │
│   │   │ Commit History  │     │ refs/notes/tdd-handoffs             │  │    │
│   │   │                 │     │                                     │  │    │
│   │   │ abc123 ─────────│────►│ {"phase": "RED", "test": "..."}    │  │    │
│   │   │ def456 ─────────│────►│ {"phase": "GREEN", "impl": "..."}  │  │    │
│   │   │ ghi789 ─────────│────►│ {"phase": "REFACTOR", ...}         │  │    │
│   │   │                 │     │                                     │  │    │
│   │   └─────────────────┘     └─────────────────────────────────────┘  │    │
│   │                                                                     │    │
│   └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Java SDK Feature Verification

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
| **Model Selection** | ✅ Yes | `Model.CLAUDE_OPUS_4_5_20251101` enum |
| **Conversation History** | ✅ Yes | List of `MessageParam` for multi-turn conversations |
| **Beta Features** | ✅ Yes | `BetaTool`, `BetaMessage` for experimental features |

### Features Requiring Manual Implementation

Unlike the Python Claude Agent SDK, the following require custom code:

| Feature | Python SDK | Java SDK Equivalent |
|---------|------------|---------------------|
| `AgentDefinition` class | Built-in | Custom record class |
| `query()` function | Built-in | Manual message creation + tool loop |
| Session ID tracking | Built-in | Manual state management |
| Task tool (subagents) | Built-in | Manual orchestration pattern |
| MCP tool decorators | `@tool` decorator | `Tool.builder()` with JSON Schema |
| Async iteration | `async for msg in query()` | `CompletableFuture` chains |

---

## 3. SDK Installation & Setup

### 3.1 Maven (`pom.xml`)

```xml
<dependencies>
    <!-- Anthropic Java SDK -->
    <dependency>
        <groupId>com.anthropic</groupId>
        <artifactId>anthropic-java</artifactId>
        <version>2.11.1</version>
    </dependency>

    <!-- JGit for Git Notes -->
    <dependency>
        <groupId>org.eclipse.jgit</groupId>
        <artifactId>org.eclipse.jgit</artifactId>
        <version>6.10.0.202406032230-r</version>
    </dependency>

    <!-- Jackson for JSON -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.18.2</version>
    </dependency>

    <!-- SLF4J for logging -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-simple</artifactId>
        <version>2.0.9</version>
    </dependency>
</dependencies>
```

### 3.2 Gradle (`build.gradle.kts`)

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
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

application {
    mainClass.set("com.redgreenrefactor.TDDOrchestrator")
}
```

**Requirements:** Java 8 or later (Java 17+ recommended)

### 3.3 Basic Client Setup

```java
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;

public class TddOrchestrator {
    private final AnthropicClient client;

    public TddOrchestrator() {
        // Configure from ANTHROPIC_API_KEY environment variable
        this.client = AnthropicOkHttpClient.fromEnv();
    }

    public TddOrchestrator(String apiKey) {
        this.client = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .build();
    }
}
```

---

## 4. Agent Architecture for TDD

### 4.1 Session Models

```
┌────────────────────────────────────────────────────────────────────┐
│                    SESSION MODELS COMPARISON                        │
├────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  OPTION A: Independent Sessions (Recommended)                       │
│  ─────────────────────────────────────────────────────────────────  │
│                                                                     │
│    Session 1          Session 2          Session 3          Session 4
│    ┌─────────┐        ┌─────────┐        ┌─────────┐        ┌─────────┐
│    │ Test    │   →    │ Test    │   →    │ Impl    │   →    │ Refactor│
│    │ List    │  Git   │ Agent   │  Git   │ Agent   │  Git   │ Agent   │
│    │ Agent   │ Notes  │         │ Notes  │         │ Notes  │         │
│    └─────────┘        └─────────┘        └─────────┘        └─────────┘
│         ↓                  ↓                  ↓                  ↓
│    Fresh context      Fresh context      Fresh context      Fresh context
│                                                                     │
│  OPTION B: Conversation Continuity (Not recommended)                │
│  ─────────────────────────────────────────────────────────────────  │
│    Context grows: 100% → 150% → 200% → 250% of original             │
│                                                                     │
└────────────────────────────────────────────────────────────────────┘
```

**Recommendation**: Use **Option A (Independent Sessions)** with explicit context passing via Git Notes.

### 4.2 Defining the Four Agents

```java
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
        2. Create/update the test list file (test-list.md)
        3. Select the next pending test
        4. Determine when the feature is complete

        You DO NOT write actual test code—only plan tests.

        Output your selected test in a JSON block:
        ```json
        {"test": "description of the test", "complete": false}
        ```
        """,
        getAllTools(),
        Model.CLAUDE_OPUS_4_5_20251101
    );
}

public AgentDefinition testAgent() {
    return new AgentDefinition(
        "test-agent",
        "Red phase specialist that writes failing tests",
        """
        You are the Test Agent (Red Phase) in a TDD workflow.

        Your responsibilities:
        1. Receive ONE test case description
        2. Write a failing test for that case
        3. The test must compile but FAIL when run

        Write minimal, focused tests. Commit with message starting with "test:".
        """,
        getAllTools(),
        Model.CLAUDE_OPUS_4_5_20251101
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

        Do NOT over-engineer. Write just enough code.
        Commit with message starting with "feat:" or "fix:".
        """,
        getAllTools(),
        Model.CLAUDE_OPUS_4_5_20251101
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

        May refactor any code in the codebase.
        Commit with message starting with "refactor:".
        """,
        getAllTools(),
        Model.CLAUDE_OPUS_4_5_20251101
    );
}

private List<Tool> getAllTools() {
    return List.of(
        createReadTool(),
        createWriteTool(),
        createEditTool(),
        createBashTool(),
        createGlobTool(),
        createGrepTool()
    );
}
```

### 4.3 Tool Access

All agents have access to all tools:

| Agent | read_file | write_file | edit_file | bash | glob | grep |
|-------|-----------|------------|-----------|------|------|------|
| Test List | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Test | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Implementing | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Refactor | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

**Rationale:**
- Agents self-regulate based on their system prompts and role definitions
- Full tool access allows agents to handle edge cases and unexpected situations
- Simpler implementation without per-agent tool filtering logic

---

## 5. Tool Definitions via JSON Schema

```java
import com.anthropic.models.messages.Tool;
import com.anthropic.core.JsonValue;

public class TDDTools {

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

---

## 6. Git Notes Deep Dive

### 6.1 Git Notes Fundamentals

Git Notes attach metadata to commits without modifying them:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         GIT NOTES ARCHITECTURE                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   COMMIT OBJECT                         NOTES REFERENCE                      │
│   ┌─────────────────┐                  ┌─────────────────────────────────┐  │
│   │ SHA: abc123...  │                  │ refs/notes/commits              │  │
│   │ Tree: ...       │   ◄──attached──  │   └── abc123 → "Note content"  │  │
│   │ Parent: ...     │                  │                                 │  │
│   │ Author: ...     │                  │ refs/notes/tdd-handoffs         │  │
│   │ Message: ...    │   ◄──attached──  │   └── abc123 → {"phase":...}   │  │
│   └─────────────────┘                  │                                 │  │
│                                        │ refs/notes/ci-results           │  │
│   SHA-1 UNCHANGED!                     │   └── abc123 → "PASS"          │  │
│   (Note doesn't modify commit)         └─────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Core Git Notes Commands

| Command | Purpose |
|---------|---------|
| `git notes add -m "content"` | Add note to HEAD commit |
| `git notes add -m "content" <commit>` | Add note to specific commit |
| `git notes show <commit>` | View note attached to commit |
| `git notes edit <commit>` | Edit existing note |
| `git notes remove <commit>` | Delete note from commit |
| `git notes --ref=<namespace>` | Use custom namespace |
| `git log --show-notes` | Show notes in log output |

### 6.3 Namespaces for TDD Workflow

**Recommended Namespaces:**

| Namespace | Purpose |
|-----------|---------|
| `refs/notes/tdd-handoffs` | Primary handoff state between agents |
| `refs/notes/tdd-test-list` | Test list state and progress |
| `refs/notes/tdd-errors` | Error tracking and recovery info |
| `refs/notes/tdd-metrics` | Timing and performance data |

```bash
# Create handoff note in TDD namespace
git notes --ref=tdd-handoffs add -m '{"phase":"RED","test":"user login"}' HEAD

# Read handoff note
git notes --ref=tdd-handoffs show HEAD

# List all notes in namespace
git notes --ref=tdd-handoffs list
```

### 6.4 Sharing Notes Between Repositories

Notes require explicit push/pull:

```bash
# Push TDD handoff notes to remote
git push origin refs/notes/tdd-handoffs

# Pull TDD handoff notes from remote
git fetch origin refs/notes/tdd-handoffs:refs/notes/tdd-handoffs
```

**Automation via Git Config:**
```bash
# Add to .git/config for automatic note sync
[remote "origin"]
    fetch = +refs/notes/*:refs/notes/*
    push = +refs/notes/*:refs/notes/*
```

---

## 7. JGit Integration for Git Notes

### 7.1 Git Notes Operations in Java

```java
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.notes.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GitNotesManager {
    private final Repository repository;
    private final ObjectMapper objectMapper;
    private static final String TDD_HANDOFFS_REF = "refs/notes/tdd-handoffs";

    public GitNotesManager(String repoPath) throws IOException {
        this.repository = new FileRepositoryBuilder()
            .setGitDir(new File(repoPath, ".git"))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Write handoff state as a note on the given commit
     */
    public void writeHandoff(ObjectId commitId, HandoffState state) throws Exception {
        try (Git git = new Git(repository)) {
            String json = objectMapper.writeValueAsString(state);

            git.notesAdd()
                .setNotesRef(TDD_HANDOFFS_REF)
                .setObjectId(repository.parseCommit(commitId))
                .setMessage(json)
                .call();
        }
    }

    /**
     * Read handoff state from a note on the given commit
     */
    public HandoffState readHandoff(ObjectId commitId) throws Exception {
        NoteMap noteMap = NoteMap.read(
            repository.newObjectReader(),
            repository.resolve(TDD_HANDOFFS_REF)
        );

        Note note = noteMap.getNote(commitId);
        if (note == null) {
            return null;
        }

        ObjectLoader loader = repository.open(note.getData());
        String json = new String(loader.getBytes(), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, HandoffState.class);
    }

    /**
     * Get the latest commit with a handoff note
     */
    public ObjectId findLatestHandoff() throws Exception {
        try (Git git = new Git(repository)) {
            Iterable<RevCommit> commits = git.log()
                .setMaxCount(100)
                .call();

            NoteMap noteMap = NoteMap.read(
                repository.newObjectReader(),
                repository.resolve(TDD_HANDOFFS_REF)
            );

            for (RevCommit commit : commits) {
                if (noteMap.getNote(commit.getId()) != null) {
                    return commit.getId();
                }
            }
        }
        return null;
    }
}
```

### 7.2 Handoff State Data Model

```java
import com.fasterxml.jackson.annotation.JsonProperty;

public class HandoffState {
    public enum Phase {
        PLAN, RED, GREEN, REFACTOR, COMPLETE
    }

    @JsonProperty("phase")
    private Phase currentPhase;

    @JsonProperty("next_phase")
    private Phase nextPhase;

    @JsonProperty("cycle")
    private int cycleNumber;

    @JsonProperty("current_test")
    private TestCase currentTest;

    @JsonProperty("completed_tests")
    private List<String> completedTests;

    @JsonProperty("pending_tests")
    private List<String> pendingTests;

    @JsonProperty("test_result")
    private String testResult;

    @JsonProperty("error")
    private String error;

    @JsonProperty("timestamp")
    private Instant timestamp;

    // Getters, setters, builders...
}

public class TestCase {
    @JsonProperty("description")
    private String description;

    @JsonProperty("test_file")
    private String testFile;

    @JsonProperty("impl_file")
    private String implFile;

    // Getters, setters...
}
```

---

## 8. Complete Orchestrator Implementation

### 8.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    JAVA TDD ORCHESTRATOR - CLASS DIAGRAM                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         TddOrchestrator                              │    │
│  │  - anthropicClient: AnthropicClient                                  │    │
│  │  - gitNotesManager: GitNotesManager                                  │    │
│  │  - agents: Map<Phase, AgentConfig>                                   │    │
│  │  + runCycle(featureRequest): CycleResult                             │    │
│  │  + runFullWorkflow(featureRequest): WorkflowResult                   │    │
│  └────────────────────────────────┬────────────────────────────────────┘    │
│                                   │                                          │
│            ┌──────────────────────┼──────────────────────┐                  │
│            │                      │                      │                  │
│            ▼                      ▼                      ▼                  │
│  ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────────────┐   │
│  │  AgentConfig    │   │ GitNotesManager │   │    HandoffState         │   │
│  │  - name         │   │ - repository    │   │    - phase              │   │
│  │  - systemPrompt │   │ + writeHandoff()│   │    - cycleNumber        │   │
│  │  - model        │   │ + readHandoff() │   │    - currentTest        │   │
│  │  - tools        │   │ + findLatest()  │   │    - completedTests     │   │
│  └─────────────────┘   └─────────────────┘   └─────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 Main Orchestrator Class

```java
package com.redgreenrefactor.orchestrator;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;

import java.nio.file.Path;
import java.util.*;

public class TddOrchestrator {
    private final AnthropicClient anthropicClient;
    private final GitNotesManager gitNotesManager;
    private final GitOperations gitOps;
    private final Path projectRoot;

    private static final Map<HandoffState.Phase, AgentConfig> AGENT_CONFIGS = Map.of(
        HandoffState.Phase.PLAN, new AgentConfig(
            "Test List Agent",
            """
            You are the Test List Agent in a TDD workflow.
            Your responsibilities:
            1. Analyze feature requirements
            2. Create/update the test list file (test-list.md)
            3. Select the next pending test
            4. Determine when the feature is complete

            You DO NOT write actual test code—only plan tests.

            Output your selected test in a JSON block:
            ```json
            {"test": "description of the test", "complete": false}
            ```
            """,
            Model.CLAUDE_OPUS_4_5_20251101
        ),
        HandoffState.Phase.RED, new AgentConfig(
            "Test Agent",
            """
            You are the Test Agent (Red Phase) in a TDD workflow.
            Your responsibilities:
            1. Receive ONE test case description
            2. Write a failing test for that case
            3. The test must compile but FAIL when run

            Write minimal, focused tests. Commit with message starting with "test:".
            """,
            Model.CLAUDE_OPUS_4_5_20251101
        ),
        HandoffState.Phase.GREEN, new AgentConfig(
            "Implementing Agent",
            """
            You are the Implementing Agent (Green Phase) in a TDD workflow.
            Your responsibilities:
            1. Read the failing test
            2. Write MINIMUM code to make it pass
            3. Ensure all tests pass

            Do NOT over-engineer. Write just enough code.
            Commit with message starting with "feat:" or "fix:".
            """,
            Model.CLAUDE_OPUS_4_5_20251101
        ),
        HandoffState.Phase.REFACTOR, new AgentConfig(
            "Refactor Agent",
            """
            You are the Refactor Agent in a TDD workflow.
            Your responsibilities:
            1. Review implementation AND test code
            2. Refactor for clarity, maintainability
            3. Ensure all tests still pass

            May refactor any code in the codebase.
            Commit with message starting with "refactor:".
            """,
            Model.CLAUDE_OPUS_4_5_20251101
        )
    );

    public TddOrchestrator(Path projectRoot) throws Exception {
        this.projectRoot = projectRoot;
        this.anthropicClient = AnthropicOkHttpClient.fromEnv();
        this.gitNotesManager = new GitNotesManager(projectRoot.toString());
        this.gitOps = new GitOperations(projectRoot);
    }

    /**
     * Run a complete TDD workflow for a feature
     */
    public WorkflowResult runWorkflow(String featureRequest) {
        List<CycleResult> cycles = new ArrayList<>();

        try {
            HandoffState state = runPlanningPhase(featureRequest);

            while (state.getCurrentPhase() != HandoffState.Phase.COMPLETE) {
                CycleResult cycle = runCycle(state);
                cycles.add(cycle);

                if (!cycle.isSuccess()) {
                    return new WorkflowResult(false, cycles, cycle.getError());
                }

                state = cycle.getFinalState();
            }

            return new WorkflowResult(true, cycles, null);

        } catch (Exception e) {
            return new WorkflowResult(false, cycles, e.getMessage());
        }
    }

    /**
     * Run a single TDD cycle (Red -> Green -> Refactor -> Plan)
     */
    public CycleResult runCycle(HandoffState initialState) {
        HandoffState state = initialState;

        try {
            state = runPhase(HandoffState.Phase.RED, state);
            state = runPhase(HandoffState.Phase.GREEN, state);
            state = runPhase(HandoffState.Phase.REFACTOR, state);
            state = runPhase(HandoffState.Phase.PLAN, state);

            return new CycleResult(true, state, null);

        } catch (Exception e) {
            return new CycleResult(false, state, e.getMessage());
        }
    }

    private HandoffState runPhase(HandoffState.Phase phase, HandoffState currentState)
            throws Exception {
        AgentConfig config = AGENT_CONFIGS.get(phase);

        String prompt = buildPromptWithContext(config, currentState);
        Message response = invokeAgent(config, prompt);
        PhaseResult result = processAgentResponse(phase, response);

        ObjectId commitId = gitOps.commitChanges(result.getCommitMessage());

        HandoffState nextState = currentState.toBuilder()
            .currentPhase(phase)
            .nextPhase(getNextPhase(phase, result))
            .timestamp(Instant.now())
            .build();

        gitNotesManager.writeHandoff(commitId, nextState);

        return nextState;
    }

    private Message invokeAgent(AgentConfig config, String prompt) {
        MessageCreateParams params = MessageCreateParams.builder()
            .model(config.getModel())
            .maxTokens(8192L)
            .system(config.getSystemPrompt())
            .addUserMessage(prompt)
            .build();

        return anthropicClient.messages().create(params);
    }

    /**
     * Resume workflow from the last known state
     */
    public HandoffState resumeFromLastHandoff() throws Exception {
        ObjectId lastCommit = gitNotesManager.findLatestHandoff();

        if (lastCommit == null) {
            return HandoffState.builder()
                .currentPhase(HandoffState.Phase.PLAN)
                .cycleNumber(1)
                .pendingTests(new ArrayList<>())
                .completedTests(new ArrayList<>())
                .build();
        }

        return gitNotesManager.readHandoff(lastCommit);
    }
}
```

### 8.3 Streaming for Real-Time Feedback

```java
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.helpers.MessageAccumulator;

public Message invokeWithStreaming(String taskPrompt, Consumer<String> onToken) {
    MessageCreateParams params = MessageCreateParams.builder()
        .model(Model.CLAUDE_OPUS_4_5_20251101)
        .maxTokens(4096L)
        .system(systemPrompt)
        .addUserMessage(taskPrompt)
        .build();

    MessageAccumulator accumulator = MessageAccumulator.create();

    try (StreamResponse<RawMessageStreamEvent> stream =
            client.messages().createStreaming(params)) {

        stream.stream()
            .peek(accumulator::accumulate)
            .flatMap(event -> event.contentBlockDelta().stream())
            .flatMap(delta -> delta.delta().text().stream())
            .forEach(textDelta -> onToken.accept(textDelta.text()));
    }

    return accumulator.message();
}
```

### 8.4 Async Agent Orchestration

```java
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync;
import java.util.concurrent.CompletableFuture;

public class AsyncTddOrchestrator {
    private final AnthropicClientAsync client;

    public AsyncTddOrchestrator() {
        this.client = AnthropicOkHttpClientAsync.fromEnv();
    }

    public CompletableFuture<Message> invokeAgentAsync(String systemPrompt, String task) {
        MessageCreateParams params = MessageCreateParams.builder()
            .model(Model.CLAUDE_OPUS_4_5_20251101)
            .maxTokens(4096L)
            .system(systemPrompt)
            .addUserMessage(task)
            .build();

        return client.messages().create(params);
    }

    /**
     * Run TDD cycle with async/await pattern
     */
    public CompletableFuture<Void> runCycleAsync(String featureRequest) {
        return runTestListAgentAsync(featureRequest)
            .thenCompose(v -> runTestAgentAsync())
            .thenCompose(v -> runImplementingAgentAsync())
            .thenCompose(v -> runRefactorAgentAsync())
            .thenRun(() -> System.out.println("Cycle complete!"));
    }
}
```

---

## 9. Handoff Flow with Git Notes

### 9.1 Complete Handoff Sequence

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    GIT NOTES HANDOFF SEQUENCE                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  COMMIT 1: abc123 (Plan)                                                     │
│  ├── Message: "plan: create test list for user auth"                        │
│  ├── Files: test-list.md                                                    │
│  └── Note (refs/notes/tdd-handoffs):                                        │
│      {                                                                       │
│        "phase": "PLAN",                                                      │
│        "next_phase": "RED",                                                  │
│        "cycle": 1,                                                           │
│        "current_test": {                                                     │
│          "description": "User can log in with valid credentials"            │
│        },                                                                    │
│        "pending_tests": ["User can log out", "Invalid creds return error"], │
│        "completed_tests": []                                                 │
│      }                                                                       │
│                           │                                                  │
│                           ▼                                                  │
│  COMMIT 2: def456 (Red)                                                      │
│  ├── Message: "test: add failing test for user login"                       │
│  ├── Files: tests/test_user_login.py                                        │
│  └── Note (refs/notes/tdd-handoffs):                                        │
│      {                                                                       │
│        "phase": "RED",                                                       │
│        "next_phase": "GREEN",                                                │
│        "cycle": 1,                                                           │
│        "test_result": "FAIL"                                                 │
│      }                                                                       │
│                           │                                                  │
│                           ▼                                                  │
│  COMMIT 3: ghi789 (Green)                                                    │
│  ├── Message: "feat: implement user login"                                  │
│  ├── Files: src/auth/login.py                                               │
│  └── Note (refs/notes/tdd-handoffs):                                        │
│      {                                                                       │
│        "phase": "GREEN",                                                     │
│        "next_phase": "REFACTOR",                                             │
│        "cycle": 1,                                                           │
│        "test_result": "PASS"                                                 │
│      }                                                                       │
│                           │                                                  │
│                           ▼                                                  │
│  COMMIT 4: jkl012 (Refactor)                                                 │
│  ├── Message: "refactor: clean up login implementation"                     │
│  ├── Files: src/auth/login.py, tests/test_user_login.py                     │
│  └── Note (refs/notes/tdd-handoffs):                                        │
│      {                                                                       │
│        "phase": "REFACTOR",                                                  │
│        "next_phase": "PLAN",                                                 │
│        "cycle": 1,                                                           │
│        "completed_tests": ["User can log in with valid credentials"],       │
│        "test_result": "PASS"                                                 │
│      }                                                                       │
│                           │                                                  │
│                           ▼                                                  │
│  COMMIT 5: mno345 (Plan - Cycle 2)                                           │
│  └── ... next cycle begins                                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.2 Data Sources for Next Agent

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DATA SOURCES FOR NEXT AGENT                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   DATA SOURCE 1: GIT COMMITS                                                │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │  • Commit SHA and message                                            │   │
│   │  • File changes (diffs)                                              │   │
│   │  • Author and timestamp                                              │   │
│   │  • Provides: Code context, change history, implementation details    │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│   DATA SOURCE 2: GIT NOTES                                                  │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │  • Phase state (PLAN, RED, GREEN, REFACTOR)                         │   │
│   │  • Next phase indicator                                              │   │
│   │  • Current and completed tests                                       │   │
│   │  • Error states and recovery context                                 │   │
│   │  • Provides: Workflow state, handoff context, orchestration data     │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│                              │                                               │
│                              ▼                                               │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                      NEXT AGENT                                      │   │
│   │  Receives combined context:                                          │   │
│   │  • What code was changed (from commits)                              │   │
│   │  • What phase to execute (from notes)                                │   │
│   │  • What tests are pending/completed (from notes)                     │   │
│   │  • Full audit trail for decision-making                              │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Error Handling and Recovery

### 10.1 Detecting Failures

```java
public class TDDErrorHandler {

    public boolean isTestFailure(String bashOutput) {
        return bashOutput.contains("FAILED") ||
               bashOutput.contains("FAIL:") ||
               bashOutput.contains("Error") ||
               bashOutput.matches(".*Exit code: [^0].*");
    }

    public boolean isUnexpectedPass(String bashOutput, String phase) {
        if ("red".equals(phase)) {
            return bashOutput.contains("OK") ||
                   bashOutput.contains("passed") ||
                   bashOutput.matches(".*Exit code: 0.*");
        }
        return false;
    }
}
```

### 10.2 Retry with Exponential Backoff

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
}
```

### 10.3 Recovery Strategies

| Failure Type | Recovery Approach |
|--------------|-------------------|
| Test doesn't compile | Re-run test-agent with error context in prompt |
| Test passes immediately | Re-run test-agent with instruction to check assertions |
| Implementation breaks other tests | Rollback via git, try alternative approach |
| Refactoring breaks tests | Rollback via git, re-run refactor-agent |
| API rate limit | Exponential backoff with retry |
| Network timeout | Retry with increased timeout |

### 10.4 Error State in Notes

```java
public void recordError(ObjectId commitId, Exception error, HandoffState state)
        throws Exception {
    HandoffState errorState = state.toBuilder()
        .error(error.getMessage())
        .errorDetails(new ErrorDetails(
            error.getClass().getName(),
            Arrays.toString(error.getStackTrace()),
            Instant.now()
        ))
        .build();

    gitNotesManager.writeHandoff(commitId, errorState);
}
```

---

## 11. Comparison: Java SDK vs Python SDK

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

## 12. Advantages of This Hybrid Approach

### 12.1 Comparison Matrix

| Criterion | File-Only | SDK-Only | **Java + Git Notes** |
|-----------|-----------|----------|----------------------|
| **Type Safety** | None | Partial | Full (Java) |
| **Audit Trail** | File history | None | Commit-attached |
| **No External Deps** | Yes | No | Yes (Git only) |
| **Recovery** | Manual | Session-based | Note-based |
| **Parallel Workflows** | File conflicts | Complex | Note namespaces |
| **Human Readable** | JSON files | Logs | `git notes show` |
| **Portability** | High | SDK-specific | JVM + Git |
| **CI/CD Integration** | Good | Complex | Native |

### 12.2 Unique Benefits

1. **Immutable Handoff Records**: Notes are version-controlled; handoff history is never lost
2. **Atomic Phase Completion**: Each commit + note = one atomic handoff
3. **Branch-Friendly**: Different branches can have different handoff states
4. **Merge-Friendly**: Notes merge with commits during git merge
5. **Offline Capable**: All state is local; no network dependency between phases
6. **Debuggable**: `git log --show-notes=tdd-handoffs` shows complete history

### 12.3 Potential Challenges

| Challenge | Mitigation |
|-----------|------------|
| Notes require explicit push | Add to `.git/config` or CI scripts |
| JGit learning curve | Well-documented, stable API |
| Note conflicts on same commit | Use append mode or merge strategy |
| UI support limited | CLI is sufficient; custom tooling possible |

---

## 13. Implementation Roadmap

### Phase 1: Foundation
- Set up Java project with Maven/Gradle
- Integrate Anthropic Java SDK
- Integrate JGit for Git Notes operations
- Define HandoffState data model

### Phase 2: Core Agents
- Implement agent invocation with streaming
- Build prompt templates for each phase
- Create Git commit + note write pipeline

### Phase 3: Orchestration
- Build main orchestrator loop
- Add resume/recovery logic
- Implement error handling with notes

### Phase 4: Enhancement
- Add metrics/timing to notes
- Build CLI interface
- Create visualization tools for handoff history

---

## 14. Open Questions for Implementation

1. **Spring AI Integration**: Should we use Spring AI's AnthropicChatModel for better DI support?
2. **Parallel cycles**: Can multiple TDD cycles run in parallel using CompletableFuture?
3. **Human approval gates**: Should there be optional pause points for human review?
4. **Rollback granularity**: Should rollback be to last commit or last known-good state?
5. **Test framework detection**: Should orchestrator auto-detect JUnit/TestNG/etc.?
6. **Tool execution sandboxing**: How to safely execute bash commands?

---

## 15. References

- [Anthropic Java SDK - GitHub](https://github.com/anthropics/anthropic-sdk-java)
- [Anthropic Java SDK - Maven Central](https://central.sonatype.com/artifact/com.anthropic/anthropic-java)
- [Git Notes & Trailers - Best Practices](https://risadams.com/blog/2025/04/17/git-notes/)
- [Git Notes for CI/CD](https://medium.com/digitalfrontiers/git-your-stuff-together-storing-test-reports-along-your-sources-with-git-notes-f5c8068dc981)
- [JGit Documentation](https://www.eclipse.org/jgit/)

---

## Appendix A: Git Notes Quick Reference

```bash
# === SETUP ===
# Enable automatic note sync with remote
git config --add remote.origin.fetch '+refs/notes/*:refs/notes/*'
git config --add remote.origin.push '+refs/notes/*:refs/notes/*'

# === WRITING NOTES ===
# Add note to current commit
git notes --ref=tdd-handoffs add -m '{"phase":"PLAN"}' HEAD

# Add note to specific commit
git notes --ref=tdd-handoffs add -m '{"phase":"RED"}' abc123

# Force overwrite existing note
git notes --ref=tdd-handoffs add -f -m '{"phase":"GREEN"}' HEAD

# === READING NOTES ===
# Show note on current commit
git notes --ref=tdd-handoffs show HEAD

# Show note on specific commit
git notes --ref=tdd-handoffs show abc123

# List all notes
git notes --ref=tdd-handoffs list

# Show log with notes
git log --show-notes=tdd-handoffs

# === SYNCING ===
# Push notes to remote
git push origin refs/notes/tdd-handoffs

# Fetch notes from remote
git fetch origin refs/notes/tdd-handoffs:refs/notes/tdd-handoffs
```

## Appendix B: Required Imports

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

// JGit
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.notes.*;

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
import com.fasterxml.jackson.annotation.JsonProperty;
```
