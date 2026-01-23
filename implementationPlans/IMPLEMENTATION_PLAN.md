# RedGreenRefactor Implementation Plan

This document provides a detailed implementation plan for building the multi-agent TDD orchestrator using the Anthropic Java SDK and Git Notes for handoff coordination.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         TDD ORCHESTRATOR                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌──────────────┐   ┌──────────────┐   ┌──────────────────────────┐   │
│   │ Anthropic    │   │ JGit         │   │ State Manager            │   │
│   │ Java SDK     │   │ Library      │   │ (Git Notes)              │   │
│   │              │   │              │   │                          │   │
│   │ - Agents     │   │ - Read Notes │   │ - HandoffState           │   │
│   │ - Tools      │   │ - Write Notes│   │ - Phase tracking         │   │
│   │ - Streaming  │   │ - Commits    │   │ - Error recovery         │   │
│   └──────┬───────┘   └──────┬───────┘   └───────────┬──────────────┘   │
│          │                  │                       │                   │
│          └──────────────────┴───────────────────────┘                   │
│                             │                                            │
│                             ▼                                            │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                    GIT REPOSITORY                                │   │
│   │   Commits + refs/notes/tdd-handoffs                             │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Project Foundation

### 1.1 Project Setup
- [ ] Create Java project structure with Maven
- [ ] Configure `pom.xml` with all dependencies:
  - `com.anthropic:anthropic-java:2.11.1`
  - `org.eclipse.jgit:org.eclipse.jgit:7.5.0.202512021534-r`
  - `com.fasterxml.jackson.core:jackson-databind:2.21.0`
  - `org.slf4j:slf4j-simple:2.0.17`
- [ ] Set up Java 21 toolchain
- [ ] Create package structure: `com.redgreenrefactor.{orchestrator,agent,git,tool,model}`

### 1.2 Data Models
- [ ] Create `HandoffState` record with JSON annotations (camelCase throughout):
  - `phase` (enum: PLAN, RED, GREEN, REFACTOR, COMPLETE)
  - `nextPhase`
  - `cycleNumber`
  - `currentTest` (TestCase)
  - `completedTests` (List<String>)
  - `pendingTests` (List<String>)
  - `testResult` (enum: PASS, FAIL, nullable)
  - `error` (nullable String)
  - `errorDetails` (nullable object with `type` and `message`)
  - `retryCount` (int, default 0)
- [ ] Create `TestCase` record:
  - `description`
  - `testFile`
  - `implFile`
- [ ] Create `AgentConfig` record:
  - `name`
  - `description`
  - `systemPrompt`
  - `tools` (List<Tool>) - all agents have access to all tools
  - `model`
- [ ] Create `CycleResult` and `WorkflowResult` records

### 1.3 Verification Checklist
- [ ] Verify project compiles: `mvn compile`
- [ ] Verify dependencies resolve correctly
- [ ] Verify JSON serialization/deserialization works for HandoffState
- [ ] Verify data models have proper equals/hashCode/toString

---

## Phase 2: Git Notes Integration

### 2.1 GitNotesManager
- [ ] Implement `GitNotesManager` class with JGit:
  - Constructor accepting repository path
  - `writeHandoff(ObjectId commitId, HandoffState state)` - write JSON note
  - `readHandoff(ObjectId commitId)` - read and parse JSON note
  - `findLatestHandoff()` - find most recent commit with handoff note
  - `listAllHandoffs()` - list all commits with handoff notes
- [ ] Use namespace `refs/notes/tdd-handoffs`
- [ ] Handle note overwrites with force flag

### 2.2 GitOperations
- [ ] Implement `GitOperations` class:
  - `commitChanges(String message)` - stage all and commit
  - `getLatestCommit()` - get HEAD commit ID
  - `rollbackToCommit(ObjectId commitId)` - reset to previous state
  - `getCommitDiff(ObjectId commitId)` - get changes in commit
- [ ] Handle Git errors gracefully

### 2.3 Verification Checklist
- [ ] Test writing a handoff note to a commit
- [ ] Test reading a handoff note from a commit
- [ ] Test finding latest handoff in commit history
- [ ] Test note persistence across Git operations
- [ ] Verify `git notes --ref=tdd-handoffs show HEAD` works from CLI

---

## Phase 3: Tool Definitions and Execution

### 3.1 Tool Definitions (JSON Schema)
- [ ] Implement `TDDTools` class with static tool builders (PascalCase names):
  - `createReadTool()` - "Read" - read file contents
  - `createWriteTool()` - "Write" - write file contents
  - `createEditTool()` - "Edit" - edit file by replacing text
  - `createBashTool()` - "Bash" - execute shell commands
  - `createGlobTool()` - "Glob" - find files by pattern
  - `createGrepTool()` - "Grep" - search for patterns in files
- [ ] Each tool uses `Tool.builder()` with proper JSON Schema

### 3.2 Tool Execution Handlers
- [ ] Implement `ToolExecutor` interface
- [ ] Implement handlers for each tool:
  - `ReadToolHandler` - uses `Files.readString()`
  - `WriteToolHandler` - uses `Files.writeString()`
  - `EditToolHandler` - find and replace in file
  - `BashToolHandler` - uses `ProcessBuilder`
  - `GlobToolHandler` - uses `PathMatcher`
  - `GrepToolHandler` - uses regex search
- [ ] Create `ToolDispatcher` to route tool calls to handlers
- [ ] Implement timeout handling for bash commands

### 3.3 Verification Checklist
- [ ] Test Read tool reads file correctly
- [ ] Test Write tool creates/overwrites files
- [ ] Test Edit tool performs text replacement
- [ ] Test Bash tool executes commands and captures output
- [ ] Test Glob tool finds files matching patterns
- [ ] Test Grep tool finds text patterns

---

## Phase 4: Agent Definitions

### 4.1 Test List Agent (Planning)
- [ ] Define system prompt:
  - Analyze feature requirements
  - Create/update test list file (`test-list.md` in project root, using markdown checkboxes)
  - Select next pending test
  - Output JSON with full TestCase:
    ```json
    {
      "currentTest": {
        "description": "test description",
        "testFile": "src/test/java/...",
        "implFile": "src/main/java/..."
      }
    }
    ```
    Or when all tests complete: `{"currentTest": null}`
  - Commit with "plan:" prefix via Bash tool
  - Orchestrator determines feature completion by checking if all tests in `test-list.md` are marked `[x]`
- [ ] Tools: all (Read, Write, Edit, Bash, Glob, Grep)
- [ ] Model: Claude Opus 4.5

### 4.2 Test Agent (Red Phase)
- [ ] Define system prompt:
  - Receive ONE test case description with file paths
  - Write a failing test for that case
  - Test must compile but FAIL when run
  - Commit with "test:" prefix via Bash tool
- [ ] Tools: all (Read, Write, Edit, Bash, Glob, Grep)
- [ ] Model: Claude Opus 4.5
- [ ] Test execution verification:
  - Run ALL tests (not just the new one)
  - Verify the new test FAILS
  - Verify all OTHER existing tests still PASS

### 4.3 Implementing Agent (Green Phase)
- [ ] Define system prompt:
  - Read the failing test
  - Write MINIMUM code to make it pass
  - Ensure all tests pass
  - Commit with "feat:" or "fix:" prefix via Bash tool
- [ ] Tools: all (Read, Write, Edit, Bash, Glob, Grep)
- [ ] Model: Claude Opus 4.5
- [ ] Must verify all tests pass before completing

### 4.4 Refactor Agent
- [ ] Define system prompt:
  - Review implementation AND test code
  - Refactor for clarity, maintainability
  - Ensure all tests still pass
  - Commit with "refactor:" prefix via Bash tool
  - If no refactoring needed, use `git commit --allow-empty -m "refactor: no changes needed"`
- [ ] Tools: all (Read, Write, Edit, Bash, Glob, Grep)
- [ ] Model: Claude Opus 4.5
- [ ] Must verify all tests pass after refactoring

### 4.5 Verification Checklist
- [ ] Test each agent can be invoked with Anthropic SDK
- [ ] Verify agents produce valid tool calls
- [ ] Verify commit messages follow conventions

---

## Phase 5: Orchestration Loop

### 5.1 Agent Invocation
- [ ] Implement `invokeAgent(AgentConfig, String prompt)`:
  - Build `MessageCreateParams` with model, system prompt, user message
  - Call `anthropicClient.messages().create(params)`
  - Handle tool use responses
  - Execute tools and continue conversation
  - Return final response
- [ ] Implement streaming variant for real-time feedback
- [ ] Implement async variant using `CompletableFuture`

### 5.2 Tool Use Loop
- [ ] Detect `StopReason.TOOL_USE` in response
- [ ] Parse `ToolUseBlock` to extract tool name and inputs
- [ ] Execute tool via `ToolDispatcher`
- [ ] Build `ToolResultBlockParam` with execution result
- [ ] Continue conversation with tool results
- [ ] Loop until `StopReason.END_TURN`

### 5.3 Prompt Building
- [ ] Implement `buildPromptWithContext(AgentConfig, HandoffState)`:
  - Include current phase information
  - Include current test description (for RED/GREEN/REFACTOR)
  - Include test list status (for PLAN)
  - Include previous phase results
  - Include error context for retries

### 5.4 Phase Execution
- [ ] Implement `runPhase(Phase, HandoffState)`:
  - Get agent config for phase
  - Build prompt with context
  - Invoke agent (agent commits via Bash tool)
  - Process response
  - Write handoff note to the agent's commit
  - Return updated state
  - Note: Agents are responsible for committing; orchestrator only writes Git Notes

### 5.5 Main Orchestrator
- [ ] Implement `runWorkflow(featureRequest)`:
  - Run planning phase to get initial test list
  - Loop through TDD cycles until complete
  - Fixed phase sequence: PLAN → RED → GREEN → REFACTOR → PLAN (loop) or COMPLETE
  - Detect completion when Test List Agent outputs `{"currentTest": null}` (orchestrator verifies all tests in `test-list.md` are marked `[x]`)
  - Return `WorkflowResult`

### 5.6 Verification Checklist
- [ ] Test single phase execution works end-to-end
- [ ] Test tool use loop handles multiple tool calls
- [ ] Test orchestrator runs complete TDD cycle
- [ ] Verify handoff notes are written after each phase
- [ ] Verify commits are created with correct messages
- [ ] Test workflow completes when all tests done

---

## Phase 6: Error Handling and Recovery

### 6.1 Failure Detection
- [ ] Implement `TDDErrorHandler`:
  - `isTestFailure(bashOutput)` - detect test failures
  - `isUnexpectedPass(bashOutput, phase)` - detect tests passing in RED phase
  - `isCompilationError(bashOutput)` - detect compilation failures
  - `isTimeout(exception)` - detect timeout errors

### 6.2 Retry Logic
- [ ] Implement `RetryHandler`:
  - Maximum 3 retries per phase
  - Exponential backoff: 1s, 2s, 4s
  - Include error context in retry prompt
  - Record retry count in handoff note

### 6.3 Recovery Strategies
| Failure Type | Recovery |
|--------------|----------|
| Test doesn't compile | Re-run Test Agent with error context |
| Test passes immediately (RED) | Re-run Test Agent with assertion check instruction |
| Implementation breaks tests | Rollback, try alternative approach |
| Refactoring breaks tests | Rollback to pre-refactor commit, re-run |
| API rate limit | Exponential backoff |
| Network timeout | Retry with increased timeout |

- [ ] Implement recovery handler for each failure type
- [ ] Implement `rollbackAndRetry(phase, error)`

### 6.4 Error State Recording
- [ ] Record errors in Git Notes (camelCase):
  ```json
  {
    "error": "message",
    "errorDetails": {
      "type": "TestFailure",
      "message": "Expected 200, got 404"
    },
    "retryCount": 2
  }
  ```
- [ ] Implement `recordError(commitId, exception, state)`
- [ ] After 3 retries: abort workflow entirely and record error state (no human intervention)

### 6.5 Verification Checklist
- [ ] Test retry logic with simulated failures
- [ ] Test rollback on broken tests
- [ ] Test error state is recorded in notes
- [ ] Test recovery from each failure type
- [ ] Verify max retries is enforced

---

## Phase 7: CLI and Integration

### 7.1 Command-Line Interface
- [ ] Create `TDDOrchestratorCLI` main class
- [ ] Implement commands:
  - `run <feature-request>` - run full workflow
  - `resume` - resume from last handoff state
  - `status` - show current workflow state
  - `history` - show handoff history
  - `rollback <commit>` - rollback to specific state
- [ ] Parse arguments with args4j or picocli
- [ ] Output progress with colored terminal output

### 7.2 Resume/Recovery
- [ ] Implement `resumeFromLastHandoff()`:
  - Find latest commit with handoff note
  - Read handoff state
  - Determine which phase to resume
  - Continue workflow from that point
- [ ] Handle case of no existing handoff (start fresh)

### 7.3 Configuration
- [ ] Support environment variables:
  - `ANTHROPIC_API_KEY` - API key (required)
  - `TDD_PROJECT_ROOT` - project directory (default: current)
  - `TDD_MAX_RETRIES` - retry limit (default: 3)
  - `TDD_MODEL` - model to use (default: claude-opus-4-5-20251101)
- [ ] Support config file `tdd.properties` (standard Java properties format):
  - `bash.timeout=120` - Bash command timeout in seconds (default: 120)
  - `test.command` - Override auto-detected test command (optional)
- [ ] Auto-detect test framework from project files:
  - `pom.xml` with JUnit → `mvn test`
  - `build.gradle` or `build.gradle.kts` → `./gradlew test`
  - `package.json` with test script → `npm test`
  - `pytest.ini`, `pyproject.toml`, or `setup.py` → `pytest`

### 7.4 Verification Checklist
- [ ] Test CLI runs workflow from command line
- [ ] Test resume continues from last state
- [ ] Test status shows correct state
- [ ] Test history lists all handoffs
- [ ] Verify config options are respected

---

## Phase 8: Testing

### 8.1 Unit Tests
- [ ] Test `HandoffState` serialization/deserialization
- [ ] Test `GitNotesManager` operations
- [ ] Test `ToolExecutor` implementations
- [ ] Test `ToolDispatcher` routing
- [ ] Test prompt building
- [ ] Test error detection

### 8.2 Integration Tests
- [ ] Test single phase with real Anthropic API (use mock key for CI)
- [ ] Test Git operations on test repository
- [ ] Test handoff flow between phases
- [ ] Test error recovery scenarios

### 8.3 End-to-End Tests
- [ ] Test complete TDD workflow with simple feature
- [ ] Test workflow resume after interruption
- [ ] Test workflow with intentional errors
- [ ] Test multi-cycle workflow

### 8.4 Verification Checklist
- [ ] All unit tests pass
- [ ] Integration tests pass
- [ ] E2E tests complete successfully
- [ ] Test coverage > 80%

---

## Implementation Order

The recommended implementation order to minimize dependencies:

1. **Phase 1** - Foundation (data models, project setup)
2. **Phase 2** - Git Notes (core handoff mechanism)
3. **Phase 3** - Tools (agent capabilities)
4. **Phase 4** - Agents (define all four agents)
5. **Phase 5** - Orchestration (tie it all together)
6. **Phase 6** - Error Handling (make it robust)
7. **Phase 7** - CLI (make it usable)
8. **Phase 8** - Testing (ensure quality)

---

## File Structure

```
src/
├── main/
│   └── java/
│       └── com/
│           └── redgreenrefactor/
│               ├── TddOrchestratorCLI.java           # CLI entry point
│               ├── orchestrator/
│               │   ├── TddOrchestrator.java          # Main orchestrator
│               │   ├── PhaseExecutor.java            # Phase execution logic
│               │   └── PromptBuilder.java            # Context-aware prompts
│               ├── agent/
│               │   ├── AgentConfig.java              # Agent configuration
│               │   ├── AgentInvoker.java             # Agent invocation
│               │   ├── TestListAgent.java            # Planning agent
│               │   ├── TestAgent.java                # Red phase agent
│               │   ├── ImplementingAgent.java        # Green phase agent
│               │   └── RefactorAgent.java            # Refactor agent
│               ├── git/
│               │   ├── GitNotesManager.java          # Git Notes operations
│               │   └── GitOperations.java            # Git commands
│               ├── tool/
│               │   ├── TDDTools.java                 # Tool definitions
│               │   ├── ToolExecutor.java             # Executor interface
│               │   ├── ToolDispatcher.java           # Route to handlers
│               │   ├── ReadToolHandler.java
│               │   ├── WriteToolHandler.java
│               │   ├── EditToolHandler.java
│               │   ├── BashToolHandler.java
│               │   ├── GlobToolHandler.java
│               │   └── GrepToolHandler.java
│               ├── model/
│               │   ├── HandoffState.java             # Handoff state
│               │   ├── TestCase.java                 # Test case info
│               │   ├── CycleResult.java              # Single cycle result
│               │   └── WorkflowResult.java           # Full workflow result
│               └── error/
│                   ├── TDDErrorHandler.java          # Error detection
│                   ├── RetryHandler.java             # Retry logic
│                   └── RecoveryStrategy.java         # Recovery actions
└── test/
    └── java/
        └── com/
            └── redgreenrefactor/
                ├── GitNotesManagerTest.java
                ├── ToolExecutorTest.java
                ├── TddOrchestratorTest.java
                └── IntegrationTest.java
```

---

## Dependencies Summary

```xml
<!-- pom.xml -->
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
        <version>7.5.0.202512021534-r</version>
    </dependency>

    <!-- JSON processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.21.0</version>
    </dependency>

    <!-- Logging -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.17</version>
    </dependency>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-simple</artifactId>
        <version>2.0.17</version>
        <scope>runtime</scope>
    </dependency>

    <!-- CLI parsing -->
    <dependency>
        <groupId>info.picocli</groupId>
        <artifactId>picocli</artifactId>
        <version>4.7.7</version>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.11.4</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.21.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>3.27.6</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Key Design Decisions Reminder

1. **Independent Sessions**: Each agent runs as a fresh session (no shared memory)
2. **Git as Shared State**: Repository + Git Notes = complete state
3. **One Test at a Time**: Each cycle processes exactly one test
4. **Four Commits per Cycle**: Plan → Red → Green → Refactor (agents commit via Bash)
5. **Non-intrusive Handoffs**: Git Notes don't pollute commit history (orchestrator writes notes)
6. **Retry with Context**: Failed phases include error info in retry prompts
7. **Fixed Phase Sequence**: PLAN → RED → GREEN → REFACTOR → (loop or COMPLETE). Orchestrator controls all transitions and sets `nextPhase` after each phase completes
8. **Auto-detect Test Framework**: Discover test command from project structure (first match wins)
9. **Model Strategy**: No fallback - if configured model unavailable, abort with clear error
10. **Git Notes Errors**: Fail fast with recovery guidance - provide CLI command to reset/repair notes
11. **Bash Timeout**: Single global timeout (default 120s) applies to all commands
12. **Feature Completion**: Feature is complete when all tests in `test-list.md` are marked `[x]`

---

## Detailed Design Decisions

### Data Model Implementation

**HandoffState**: Use Java `record` (immutable) with a manual `toBuilder()` method for creating modified copies:
```java
public record HandoffState(
    Phase phase,
    Phase nextPhase,
    int cycleNumber,
    TestCase currentTest,
    List<String> completedTests,
    List<String> pendingTests,
    TestResult testResult,  // enum, nullable
    String error,
    ErrorDetails errorDetails,
    int retryCount
) {
    public enum Phase { PLAN, RED, GREEN, REFACTOR, COMPLETE }
    public enum TestResult { PASS, FAIL }

    public HandoffStateBuilder toBuilder() {
        return new HandoffStateBuilder(this);
    }
}
```

**AgentConfig**: Use `tools` as the field name (not `allowedTools`):
```java
public record AgentConfig(
    String name,
    String description,
    String systemPrompt,
    List<Tool> tools,
    Model model
) {}
```

### Tool Parameter Names

Edit tool parameters must match Claude's actual interface:
- `file_path` - absolute path to file
- `old_string` - text to replace (NOT `old_text`)
- `new_string` - replacement text (NOT `new_text`)

### Test List Agent Output Format

The Test List Agent must output JSON with the full TestCase structure:
```json
{
  "currentTest": {
    "description": "test description",
    "testFile": "src/test/java/...",
    "implFile": "src/main/java/..."
  }
}
```
When all tests complete: `{"currentTest": null}`

The orchestrator determines feature completion by checking if all tests in `test-list.md` are marked `[x]`.

**Do NOT use** the simplified format `{"test": "...", "complete": false}`.

### Test Framework Auto-Detection

Priority order (first match wins):
1. `pom.xml` with JUnit → `mvn test`
2. `build.gradle` or `build.gradle.kts` → `./gradlew test`
3. `package.json` with test script → `npm test`
4. `pytest.ini`, `pyproject.toml`, or `setup.py` → `pytest`

If no framework detected and `test.command` not configured, abort with clear error message.

### Model Availability

No fallback chain. If `TDD_MODEL` (default: `claude-opus-4-5-20251101`) is unavailable:
- Abort immediately with clear error message
- Do not attempt to use alternative models
- Rationale: TDD quality depends on consistent model behavior; silent fallback may degrade results

### Git Notes Error Handling

On Git Notes read/write errors:
- Abort the current operation immediately
- Log the specific error with context
- Provide recovery command in error message:
  ```
  ERROR: Failed to read Git Notes. Run 'tdd-orchestrator repair-notes' to recover.
  ```
- Assume single orchestrator instance (no locking mechanism needed)

### Bash Timeout Configuration

Single global timeout applies to all Bash commands:
- Configured via `bash.timeout` in `tdd.properties` (default: 120 seconds)
- No per-command timeout override
- Test commands use the same timeout as other commands
- Rationale: Simplicity; test suites exceeding 2 minutes should optimize their tests

---

## Out of Scope (Initial Implementation)

The following features are deferred for future implementation:

### Pre-existing Codebase Handling
- How does the workflow handle existing codebases with existing tests?
- Should the Test List Agent account for existing tests when planning?
- Do existing tests count toward "all tests must pass"?
- Is this workflow scoped to new features only, or can it modify existing functionality?

**Initial assumption**: The orchestrator assumes a greenfield project or isolated feature development where existing tests (if any) should always pass.

---

## Related Documentation

- [Main Specification](specs/spec.md) - TDD workflow and agent roles
- [Handoffs Specification](specs/spec-handoffs.md) - Handoff mechanism details
- [Error Handling Specification](specs/spec-error-handling.md) - Recovery strategies
- [Java SDK Research](specs/research-java-sdk.md) - Implementation details
