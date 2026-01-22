# Handoffs Specification

> **Status: Designed**
>
> The handoff mechanism coordinates transitions between independent Claude Code sessions using Anthropic SDK + Git Commits + Git Notes.

## Design Decisions

| Decision | Choice |
|----------|--------|
| Session persistence | **New session each time** - every agent invocation starts a fresh Claude Code session, even when the same role runs again (e.g., Test List Agent in each cycle) |

## Overview

Since each agent runs as an independent Claude Code session (no shared memory), the handoff mechanism is essential for:
- Passing context from one session to the next
- Coordinating session transitions
- Maintaining workflow state

## Open Questions (Resolved)

> The following questions have been resolved by the chosen approach documented below.

### Handoff Mechanism *(Resolved: See "Chosen Approach" section)*

- ~~How is a session notified that it's their turn?~~ → Orchestrator invokes each agent in sequence
- ~~What triggers the transition from one session to the next?~~ → Orchestrator reads Git Notes and triggers next phase
- ~~Is there an orchestrator, or do sessions self-coordinate?~~ → **Orchestrator** coordinates all sessions
- ~~How are prompts/instructions delivered to each session?~~ → Orchestrator builds prompts with context from Git Notes

### Agent Context and Information Access *(Resolved: See "Data Sources for Agent Context")*

- ~~Does each session see the full codebase, or only specific files?~~ → Full codebase access via Git repository
- ~~Does each session know about previous cycles, or is it stateless?~~ → Context passed via Git Notes attached to commits
- ~~Does the Test Agent only receive the test description, or also the current implementation state?~~ → Receives test description + full repo access
- ~~How does a session know which role it should assume?~~ → Orchestrator provides role-specific system prompt

### Handoff Data Structure *(Resolved: See "Handoff State Structure")*

- ~~What exact information is passed from Test List Agent to Test Agent?~~ → `current_test` in Git Notes
- ~~What exact information is passed from Test Agent to Implementing Agent?~~ → `current_test.test_file` + `test_result`
- ~~What exact information is passed from Implementing Agent to Refactor Agent?~~ → `current_test.impl_file` + `test_result`
- ~~What exact information is passed from Refactor Agent back to Test List Agent?~~ → `completed_tests` updated, `next_phase: PLAN`

### Git as Shared State *(Resolved: See "Chosen Approach")*

- ~~Is git the only shared state mechanism?~~ → Yes: Git commits + Git Notes
- ~~Should there be a handoff file (e.g., `.handoff.json`) in the repository?~~ → No, use Git Notes instead (non-intrusive)
- ~~How does a session know what the previous session accomplished?~~ → Read Git Notes from latest commit

### Pre-existing Codebase *(Resolved)*

**Decision**: Support existing codebases, but only track TDD-created tests.

| Question | Answer |
|----------|--------|
| Handle existing codebases? | Yes - workflow can run on projects with existing code and tests |
| Account for existing tests when planning? | No - Test List Agent focuses only on new feature requirements |
| Existing tests count toward "all tests must pass"? | No - only tests created by the TDD workflow are tracked |
| Scoped to new features only? | Yes - workflow adds new features without modifying existing functionality |

**Rationale**: This approach allows the workflow to be used on real projects without requiring a pristine codebase, while keeping the TDD cycle focused on the new feature being developed.

---

## Chosen Approach: Anthropic SDK + Git Commits + Git Notes

After evaluating multiple approaches, the chosen handoff mechanism combines:

1. **Anthropic SDK** (Java or Python) - For invoking Claude agents with type-safe API calls
2. **Git Commits** - For atomic phase completion and code changes
3. **Git Notes** - For non-intrusive metadata-based inter-agent communication

### Why This Approach?

| Advantage | Description |
|-----------|-------------|
| **Git-Native State** | Handoff metadata lives in the repository without polluting commit history |
| **Audit Trail** | Complete traceability of agent handoffs alongside code evolution |
| **Decoupled Architecture** | Agents communicate through the repository, not direct coupling |
| **No External Dependencies** | Uses only Git - no Redis, databases, or message queues |
| **Human Readable** | Plain text or JSON, inspectable via `git notes show` |

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     HYBRID ARCHITECTURE OVERVIEW                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌────────────────────────────────────────────────────────────────────┐    │
│   │                    ORCHESTRATOR (JVM or Python)                     │    │
│   │  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐            │    │
│   │  │ Anthropic    │   │ Git          │   │ State        │            │    │
│   │  │ SDK          │   │ Library      │   │ Manager      │            │    │
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

### Data Sources for Agent Context

The next agent in the TDD workflow consumes two primary data sources:

1. **Git Commits** - The commit history and associated changes
2. **Git Notes** - Structured handoff metadata attached to commits

| Aspect | Git Commits Provide | Git Notes Provide |
|--------|---------------------|-------------------|
| **Code Changes** | Actual diffs and file modifications | — |
| **Phase State** | — | Current TDD phase and next action |
| **Test Progress** | Test file changes | Test list status (pending/complete) |
| **Context** | Implementation history | Orchestration metadata |
| **Recovery** | Rollback targets | Error details and retry context |

### Handoff State Structure

```json
{
  "phase": "GREEN",
  "next_phase": "REFACTOR",
  "cycle": 1,
  "current_test": {
    "description": "User can log in with valid credentials",
    "test_file": "tests/test_user_login.py",
    "impl_file": "src/auth/login.py"
  },
  "completed_tests": ["User model exists with email"],
  "pending_tests": ["User can log out", "Invalid creds return error"],
  "test_result": "PASS",
  "error": null,
  "timestamp": "2025-01-11T10:30:00Z"
}
```

---

## Python vs Java SDK Comparison

| Capability | Python Agent SDK | Java SDK |
|------------|-----------------|----------|
| AgentDefinition | Built-in class | Manual: `record AgentDefinition(...)` |
| Agent invocation | Built-in `query()` | Manual: message creation + tool loop |
| Subagents (Task tool) | Built-in | Manual orchestration pattern |
| Session tracking | Built-in `session_id` | Manual state management |
| Tool decorators | `@tool` decorator | `Tool.builder()` + JSON Schema |

### What the Java SDK Provides

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

### What You Must Build (Java)

1. **Agent Definitions** - Custom record class with name, prompt, tools, model
2. **Orchestration Loop** - Message creation → tool use detection → tool execution → continue
3. **State Management** - Git Notes for inter-agent coordination
4. **Tool Execution** - Implement handlers for each tool (read, write, bash, etc.)

### Bottom Line

- **Python Agent SDK**: Import → define agents → orchestration works
- **Java SDK**: Import → build orchestrator → implement tool loop → manage state manually

The Java SDK is **fully capable** but requires ~200-400 lines of orchestration code that Python gets for free
