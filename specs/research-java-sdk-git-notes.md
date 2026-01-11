# Hybrid Approach: Claude SDK (Java) + Git Notes for Agent Handoffs

> **Purpose**: In-depth research on combining the Anthropic Java SDK with Git Notes as a hybrid orchestration mechanism for the RedGreenRefactor TDD workflow.

## Executive Summary

This research explores a **hybrid architecture** that combines:
1. **Anthropic Java SDK** - For invoking Claude agents with type-safe API calls
2. **Git Notes** - For non-intrusive metadata-based inter-agent communication

This approach offers several unique advantages:
- **Language ecosystem**: Java's mature ecosystem for enterprise-grade orchestration
- **Git-native state**: Handoff metadata lives in the repository without polluting commit history
- **Audit trail**: Complete traceability of agent handoffs alongside code evolution
- **Decoupled architecture**: Agents communicate through the repository, not direct coupling

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

### 1.3 Combined Synergy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     HYBRID ARCHITECTURE OVERVIEW                              │
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
│   │   │ abc123 ─────────│────►│ {"phase": "red", "test": "..."}    │  │    │
│   │   │ def456 ─────────│────►│ {"phase": "green", "impl": "..."}  │  │    │
│   │   │ ghi789 ─────────│────►│ {"phase": "refactor", ...}         │  │    │
│   │   │                 │     │                                     │  │    │
│   │   └─────────────────┘     └─────────────────────────────────────┘  │    │
│   │                                                                     │    │
│   └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Anthropic Java SDK Deep Dive

### 2.1 SDK Installation

**Maven (`pom.xml`):**
```xml
<dependency>
    <groupId>com.anthropic</groupId>
    <artifactId>anthropic-java</artifactId>
    <version>2.11.1</version>
</dependency>
```

**Gradle (`build.gradle.kts`):**
```kotlin
implementation("com.anthropic:anthropic-java:2.11.1")
```

### 2.2 Basic Client Setup

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

### 2.3 Agent Invocation Pattern

Each TDD agent is invoked as a separate API call with a specialized system prompt:

```java
public class TddAgent {
    private final AnthropicClient client;
    private final String systemPrompt;
    private final Model model;

    public TddAgent(AnthropicClient client, String systemPrompt, Model model) {
        this.client = client;
        this.systemPrompt = systemPrompt;
        this.model = model;
    }

    public Message invoke(String taskPrompt) {
        MessageCreateParams params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4096L)
            .system(systemPrompt)
            .addUserMessage(taskPrompt)
            .build();

        return client.messages().create(params);
    }
}
```

### 2.4 Streaming for Real-Time Feedback

```java
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.helpers.MessageAccumulator;

public Message invokeWithStreaming(String taskPrompt, Consumer<String> onToken) {
    MessageCreateParams params = MessageCreateParams.builder()
        .model(Model.CLAUDE_SONNET_4_20250514)
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

### 2.5 Async Agent Orchestration

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
            .model(Model.CLAUDE_SONNET_4_20250514)
            .maxTokens(4096L)
            .system(systemPrompt)
            .addUserMessage(task)
            .build();

        return client.messages().create(params);
    }
}
```

---

## 3. Git Notes Deep Dive

### 3.1 Git Notes Fundamentals

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

### 3.2 Core Git Notes Commands

| Command | Purpose |
|---------|---------|
| `git notes add -m "content"` | Add note to HEAD commit |
| `git notes add -m "content" <commit>` | Add note to specific commit |
| `git notes show <commit>` | View note attached to commit |
| `git notes edit <commit>` | Edit existing note |
| `git notes remove <commit>` | Delete note from commit |
| `git notes --ref=<namespace>` | Use custom namespace |
| `git log --show-notes` | Show notes in log output |

### 3.3 Namespaces for TDD Workflow

Using namespaces to organize different handoff types:

```bash
# Create handoff note in TDD namespace
git notes --ref=tdd-handoffs add -m '{"phase":"red","test":"user login"}' HEAD

# Read handoff note
git notes --ref=tdd-handoffs show HEAD

# List all notes in namespace
git notes --ref=tdd-handoffs list
```

**Recommended Namespaces:**

| Namespace | Purpose |
|-----------|---------|
| `refs/notes/tdd-handoffs` | Primary handoff state between agents |
| `refs/notes/tdd-test-list` | Test list state and progress |
| `refs/notes/tdd-errors` | Error tracking and recovery info |
| `refs/notes/tdd-metrics` | Timing and performance data |

### 3.4 Sharing Notes Between Repositories

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

## 4. JGit Integration for Git Notes

### 4.1 JGit Dependency

```xml
<dependency>
    <groupId>org.eclipse.jgit</groupId>
    <artifactId>org.eclipse.jgit</artifactId>
    <version>6.10.0.202406032230-r</version>
</dependency>
```

### 4.2 Git Notes Operations in Java

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

### 4.3 Handoff State Data Model

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

## 5. Complete Hybrid Orchestrator Implementation

### 5.1 Architecture Overview

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

### 5.2 Main Orchestrator Class

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
            Model.CLAUDE_SONNET_4_20250514
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
            Model.CLAUDE_SONNET_4_20250514
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
            Model.CLAUDE_SONNET_4_20250514
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
            Model.CLAUDE_SONNET_4_20250514
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
            // Initial planning phase
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
            // Red Phase
            state = runPhase(HandoffState.Phase.RED, state);

            // Green Phase
            state = runPhase(HandoffState.Phase.GREEN, state);

            // Refactor Phase
            state = runPhase(HandoffState.Phase.REFACTOR, state);

            // Back to Planning
            state = runPhase(HandoffState.Phase.PLAN, state);

            return new CycleResult(true, state, null);

        } catch (Exception e) {
            return new CycleResult(false, state, e.getMessage());
        }
    }

    private HandoffState runPhase(HandoffState.Phase phase, HandoffState currentState)
            throws Exception {
        AgentConfig config = AGENT_CONFIGS.get(phase);

        // Build prompt with current state context
        String prompt = buildPromptWithContext(config, currentState);

        // Invoke Claude
        Message response = invokeAgent(config, prompt);

        // Process response (parse output, run commands, etc.)
        PhaseResult result = processAgentResponse(phase, response);

        // Commit changes
        ObjectId commitId = gitOps.commitChanges(result.getCommitMessage());

        // Write handoff note to commit
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

    private String buildPromptWithContext(AgentConfig config, HandoffState state) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## Current State\n");
        prompt.append("Phase: ").append(state.getCurrentPhase()).append("\n");
        prompt.append("Cycle: ").append(state.getCycleNumber()).append("\n");

        if (state.getCurrentTest() != null) {
            prompt.append("\n## Current Test\n");
            prompt.append(state.getCurrentTest().getDescription()).append("\n");
        }

        prompt.append("\n## Pending Tests\n");
        for (String test : state.getPendingTests()) {
            prompt.append("- [ ] ").append(test).append("\n");
        }

        prompt.append("\n## Completed Tests\n");
        for (String test : state.getCompletedTests()) {
            prompt.append("- [x] ").append(test).append("\n");
        }

        return prompt.toString();
    }

    private HandoffState.Phase getNextPhase(HandoffState.Phase current, PhaseResult result) {
        if (result.isFeatureComplete()) {
            return HandoffState.Phase.COMPLETE;
        }

        return switch (current) {
            case PLAN -> HandoffState.Phase.RED;
            case RED -> HandoffState.Phase.GREEN;
            case GREEN -> HandoffState.Phase.REFACTOR;
            case REFACTOR -> HandoffState.Phase.PLAN;
            case COMPLETE -> HandoffState.Phase.COMPLETE;
        };
    }
}
```

### 5.3 Reading Handoff State on Startup

```java
/**
 * Resume workflow from the last known state
 */
public HandoffState resumeFromLastHandoff() throws Exception {
    ObjectId lastCommit = gitNotesManager.findLatestHandoff();

    if (lastCommit == null) {
        // No previous handoff found - start fresh
        return HandoffState.builder()
            .currentPhase(HandoffState.Phase.PLAN)
            .cycleNumber(1)
            .pendingTests(new ArrayList<>())
            .completedTests(new ArrayList<>())
            .build();
    }

    return gitNotesManager.readHandoff(lastCommit);
}
```

---

## 6. Handoff Flow with Git Notes

### 6.1 Complete Handoff Sequence

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
│        "current_test": {                                                     │
│          "description": "User can log in with valid credentials",           │
│          "test_file": "tests/test_user_login.py"                            │
│        },                                                                    │
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
│        "current_test": {...},                                                │
│        "impl_file": "src/auth/login.py",                                    │
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

### 6.2 Querying Handoff History

```java
/**
 * Get complete handoff history for debugging/analysis
 */
public List<HandoffRecord> getHandoffHistory() throws Exception {
    List<HandoffRecord> history = new ArrayList<>();

    try (Git git = new Git(repository)) {
        Iterable<RevCommit> commits = git.log().call();

        NoteMap noteMap = NoteMap.read(
            repository.newObjectReader(),
            repository.resolve(TDD_HANDOFFS_REF)
        );

        for (RevCommit commit : commits) {
            Note note = noteMap.getNote(commit.getId());
            if (note != null) {
                HandoffState state = readNote(note);
                history.add(new HandoffRecord(
                    commit.getId(),
                    commit.getShortMessage(),
                    commit.getAuthorIdent().getWhen().toInstant(),
                    state
                ));
            }
        }
    }

    return history;
}
```

---

## 7. Error Handling and Recovery

### 7.1 Error State in Notes

When an error occurs, store recovery information in the note:

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

### 7.2 Recovery Strategies

```java
public HandoffState recoverFromError() throws Exception {
    HandoffState lastState = resumeFromLastHandoff();

    if (lastState.getError() != null) {
        System.out.println("Recovering from error: " + lastState.getError());

        // Rollback to last successful phase
        HandoffState.Phase failedPhase = lastState.getCurrentPhase();

        return switch (failedPhase) {
            case RED -> {
                // Test didn't compile or passed when it should fail
                // Retry with more context
                yield lastState.toBuilder()
                    .error(null)
                    .retryContext("Previous test failed to compile or unexpectedly passed")
                    .build();
            }
            case GREEN -> {
                // Implementation couldn't make test pass
                // Could try different approach or flag for human review
                yield lastState.toBuilder()
                    .error(null)
                    .retryContext("Previous implementation attempt failed")
                    .build();
            }
            case REFACTOR -> {
                // Refactoring broke tests - rollback and skip refactoring
                gitOps.resetToCommit(findLastGreenCommit());
                yield lastState.toBuilder()
                    .currentPhase(HandoffState.Phase.REFACTOR)
                    .nextPhase(HandoffState.Phase.PLAN)
                    .error(null)
                    .build();
            }
            default -> lastState;
        };
    }

    return lastState;
}
```

---

## 8. Advantages of This Hybrid Approach

### 8.1 Comparison Matrix

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

### 8.2 Unique Benefits

1. **Immutable Handoff Records**: Notes are version-controlled; handoff history is never lost
2. **Atomic Phase Completion**: Each commit + note = one atomic handoff
3. **Branch-Friendly**: Different branches can have different handoff states
4. **Merge-Friendly**: Notes merge with commits during git merge
5. **Offline Capable**: All state is local; no network dependency between phases
6. **Debuggable**: `git log --show-notes=tdd-handoffs` shows complete history

### 8.3 Potential Challenges

| Challenge | Mitigation |
|-----------|------------|
| Notes require explicit push | Add to `.git/config` or CI scripts |
| JGit learning curve | Well-documented, stable API |
| Note conflicts on same commit | Use append mode or merge strategy |
| UI support limited | CLI is sufficient; custom tooling possible |

---

## 9. Implementation Roadmap

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

## 10. Data Sources for Agent Context

### 10.1 Primary Data Sources

The next agent in the TDD workflow will consume two primary data sources:

1. **Git Notes** - Structured handoff metadata attached to commits
2. **Git Commits** - The commit history and associated changes

### 10.2 Data Flow Architecture

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
│   │  • Parent commit relationships                                       │   │
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
│   │                                                                      │   │
│   │  Receives combined context:                                          │   │
│   │  • What code was changed (from commits)                              │   │
│   │  • What phase to execute (from notes)                                │   │
│   │  • What tests are pending/completed (from notes)                     │   │
│   │  • Full audit trail for decision-making                              │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 10.3 Why Both Data Sources Are Required

| Aspect | Git Commits Provide | Git Notes Provide |
|--------|---------------------|-------------------|
| **Code Changes** | Actual diffs and file modifications | — |
| **Phase State** | — | Current TDD phase and next action |
| **Test Progress** | Test file changes | Test list status (pending/complete) |
| **Context** | Implementation history | Orchestration metadata |
| **Recovery** | Rollback targets | Error details and retry context |

The combination ensures the next agent has both the **code context** (what changed) and the **workflow context** (what to do next).

---

## 11. References

- [Anthropic Java SDK - GitHub](https://github.com/anthropics/anthropic-sdk-java)
- [Anthropic Java SDK - Maven Central](https://central.sonatype.com/artifact/com.anthropic/anthropic-java)
- [Git Notes & Trailers - Best Practices](https://risadams.com/blog/2025/04/17/git-notes/)
- [Git Notes for CI/CD](https://medium.com/digitalfrontiers/git-your-stuff-together-storing-test-reports-along-your-sources-with-git-notes-f5c8068dc981)
- [JGit Documentation](https://www.eclipse.org/jgit/)

---

## Appendix A: Complete Maven POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.redgreenrefactor</groupId>
    <artifactId>tdd-orchestrator</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

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
</project>
```

## Appendix B: Git Notes Quick Reference

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
