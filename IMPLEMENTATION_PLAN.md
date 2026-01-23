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
  - `org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r`
  - `com.fasterxml.jackson.core:jackson-databind:2.18.2`
  - `org.slf4j:slf4j-simple:2.0.9`
- [ ] Set up Java 21 toolchain
- [ ] Create package structure: `com.redgreenrefactor.{orchestrator,agent,git,tool,model}`

### 1.2 Data Models
- [ ] Create `HandoffState` record with JSON annotations:
  - `phase` (enum: PLAN, RED, GREEN, REFACTOR, COMPLETE)
  - `nextPhase`
  - `cycleNumber`
  - `currentTest` (TestCase)
  - `completedTests` (List<String>)
  - `pendingTests` (List<String>)
  - `testResult` (PASS/FAIL)
  - `error` (nullable)
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
- [ ] Implement `TDDTools` class with static tool builders:
  - `createReadTool()` - read file contents
  - `createWriteTool()` - write file contents
  - `createEditTool()` - edit file by replacing text
  - `createBashTool()` - execute shell commands
  - `createGlobTool()` - find files by pattern
  - `createGrepTool()` - search for patterns in files
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
- [ ] Test read_file tool reads file correctly
- [ ] Test write_file tool creates/overwrites files
- [ ] Test edit_file tool performs text replacement
- [ ] Test bash tool executes commands and captures output
- [ ] Test glob tool finds files matching patterns
- [ ] Test grep tool finds text patterns

---

## Phase 4: Agent Definitions

### 4.1 Test List Agent (Planning)
- [ ] Define system prompt:
  - Analyze feature requirements
  - Create/update test list file (test-list.md)
  - Select next pending test
  - Determine when feature is complete
  - Output JSON: `{"test": "description", "complete": false}`
  - Commit with "plan:" prefix
- [ ] Tools: all (read, write, edit, bash, glob, grep)
- [ ] Model: Claude Opus 4.5

### 4.2 Test Agent (Red Phase)
- [ ] Define system prompt:
  - Receive ONE test case description
  - Write a failing test for that case
  - Test must compile but FAIL when run
  - Commit with "test:" prefix
- [ ] Tools: all (read, write, edit, bash, glob, grep)
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
  - Commit with "feat:" or "fix:" prefix
- [ ] Tools: all (read, write, edit, bash, glob, grep)
- [ ] Model: Claude Opus 4.5
- [ ] Must verify all tests pass before completing

### 4.4 Refactor Agent
- [ ] Define system prompt:
  - Review implementation AND test code
  - Refactor for clarity, maintainability
  - Ensure all tests still pass
  - Commit with "refactor:" prefix
- [ ] Tools: all (read, write, edit, bash, glob, grep)
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
  - Invoke agent
  - Process response
  - Commit changes
  - Write handoff note
  - Return updated state

### 5.5 Main Orchestrator
- [ ] Implement `runWorkflow(featureRequest)`:
  - Run planning phase to get initial test list
  - Loop through TDD cycles until complete
  - Each cycle: RED → GREEN → REFACTOR → PLAN
  - Detect completion when Test List Agent signals done
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
- [ ] Record errors in Git Notes:
  ```json
  {
    "error": "message",
    "error_details": {
      "type": "TestFailure",
      "message": "Expected 200, got 404"
    },
    "retry_count": 2
  }
  ```
- [ ] Implement `recordError(commitId, exception, state)`

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
- [ ] Support config file `tdd.properties` (standard Java properties format)

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
│               │   └── TestListAgent.java            # Planning agent
│               │   └── TestAgent.java                # Red phase agent
│               │   └── ImplementingAgent.java        # Green phase agent
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
        <version>6.10.0.202406032230-r</version>
    </dependency>

    <!-- JSON processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.18.2</version>
    </dependency>

    <!-- Logging -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.9</version>
    </dependency>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-simple</artifactId>
        <version>2.0.9</version>
        <scope>runtime</scope>
    </dependency>

    <!-- CLI parsing -->
    <dependency>
        <groupId>info.picocli</groupId>
        <artifactId>picocli</artifactId>
        <version>4.7.5</version>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.1</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.8.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>3.24.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Key Design Decisions Reminder

1. **Independent Sessions**: Each agent runs as a fresh session (no shared memory)
2. **Git as Shared State**: Repository + Git Notes = complete state
3. **One Test at a Time**: Each cycle processes exactly one test
4. **Four Commits per Cycle**: Plan → Red → Green → Refactor
5. **Non-intrusive Handoffs**: Git Notes don't pollute commit history
6. **Retry with Context**: Failed phases include error info in retry prompts

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
