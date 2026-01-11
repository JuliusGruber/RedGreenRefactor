# Handoffs Specification

> **Status: Needs Design**
>
> The handoff mechanism is a critical component that coordinates transitions between independent Claude Code sessions. This specification needs to be fully designed.

## Design Decisions

| Decision | Choice |
|----------|--------|
| Session persistence | **New session each time** - every agent invocation starts a fresh Claude Code session, even when the same role runs again (e.g., Test List Agent in each cycle) |

## Overview

Since each agent runs as an independent Claude Code session (no shared memory), the handoff mechanism is essential for:
- Passing context from one session to the next
- Coordinating session transitions
- Maintaining workflow state

## Open Questions

### Handoff Mechanism

- How is a session notified that it's their turn?
- What triggers the transition from one session to the next?
- Is there an orchestrator, or do sessions self-coordinate?
- How are prompts/instructions delivered to each session?

### Agent Context and Information Access

- Does each session see the full codebase, or only specific files?
- Does each session know about previous cycles, or is it stateless?
- Does the Test Agent only receive the test description, or also the current implementation state?
- How does a session know which role it should assume?

### Handoff Data Structure

- What exact information is passed from Test List Agent to Test Agent?
- What exact information is passed from Test Agent to Implementing Agent?
- What exact information is passed from Implementing Agent to Refactor Agent?
- What exact information is passed from Refactor Agent back to Test List Agent?

### Git as Shared State

- Is git the only shared state mechanism?
- Should there be a handoff file (e.g., `.handoff.json`) in the repository?
- How does a session know what the previous session accomplished?

### Pre-existing Codebase

- How does the workflow handle existing codebases with existing tests?
- Should the Test List Agent account for existing tests when planning?
- Do existing tests count toward "all tests must pass"?
- Is this workflow scoped to new features only, or can it modify existing functionality?

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
3. **State Management** - `.tdd-state.json` or Git Notes for inter-agent coordination
4. **Tool Execution** - Implement handlers for each tool (read, write, bash, etc.)

### Bottom Line

- **Python Agent SDK**: Import → define agents → orchestration works
- **Java SDK**: Import → build orchestrator → implement tool loop → manage state manually

The Java SDK is **fully capable** but requires ~200-400 lines of orchestration code that Python gets for free
