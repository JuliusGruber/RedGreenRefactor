# Claude Agent SDK for Handoffs: In-Depth Research

> **Purpose**: Evaluate how the Claude Agent SDK can be used to implement handoffs between the four TDD agents in the RedGreenRefactor workflow.

## Executive Summary

The Claude Agent SDK provides native multi-agent orchestration through **subagents**, **session management**, and **context passing mechanisms** that map well to the TDD workflow's requirements. The SDK's approach differs from traditional "handoff" semantics—instead of explicit handoffs, it uses orchestrated subagent invocation and filesystem-based state sharing.

**Key findings:**
- The SDK supports both isolated sessions (independent agents) and session resumption (context continuity)
- Subagents via the `Task` tool provide the primary inter-agent communication mechanism
- State must be explicitly passed via filesystem, prompts, or session resumption—no implicit shared memory
- Tool restrictions per agent enforce separation of concerns

---

## 1. SDK Architecture Overview

### 1.1 Core Components

| Component | Description | Relevance to TDD Workflow |
|-----------|-------------|---------------------------|
| **ClaudeSDKClient** | Persistent session client | Maintains state between agent phases |
| **query()** | One-off task execution | Fresh sessions for isolated agents |
| **AgentDefinition** | Subagent configuration | Define Test/Impl/Refactor/List agents |
| **Task Tool** | Subagent invocation | Inter-agent communication |
| **MCP Tools** | Custom tool integration | TDD-specific operations |

### 1.2 Two Session Models

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
│  OPTION B: Session Resumption (Context Continuity)                  │
│  ─────────────────────────────────────────────────────────────────  │
│                                                                     │
│    Session with ID: abc-123                                         │
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

## 2. Subagent Architecture for TDD

### 2.1 Defining the Four Agents

The SDK allows defining specialized agents programmatically:

```python
from claude_agent_sdk import ClaudeAgentOptions, AgentDefinition

tdd_options = ClaudeAgentOptions(
    allowed_tools=["Read", "Write", "Edit", "Bash", "Glob", "Grep", "Task"],
    agents={
        "test-list-agent": AgentDefinition(
            description="Planning specialist that creates and maintains the test list",
            prompt="""You are the Test List Agent in a TDD workflow.

Your responsibilities:
1. Analyze feature requirements
2. Create/update the test list file
3. Select the next pending test
4. Determine when the feature is complete

You DO NOT write actual test code—only plan tests.""",
            tools=["Read", "Write", "Glob", "Grep"],
            model="sonnet"
        ),

        "test-agent": AgentDefinition(
            description="Red phase specialist that writes failing tests",
            prompt="""You are the Test Agent (Red Phase) in a TDD workflow.

Your responsibilities:
1. Receive ONE test case description
2. Write a failing test for that case
3. Verify the test fails (all other tests pass)
4. Commit the failing test

Write minimal, focused tests.""",
            tools=["Read", "Write", "Bash"],
            model="sonnet"
        ),

        "implementing-agent": AgentDefinition(
            description="Green phase specialist that makes tests pass",
            prompt="""You are the Implementing Agent (Green Phase) in a TDD workflow.

Your responsibilities:
1. Read the failing test
2. Write MINIMUM code to make it pass
3. Ensure all tests pass
4. Commit the implementation

Do NOT over-engineer. Write just enough code.""",
            tools=["Read", "Write", "Edit", "Bash"],
            model="sonnet"
        ),

        "refactor-agent": AgentDefinition(
            description="Refactor phase specialist that improves code quality",
            prompt="""You are the Refactor Agent in a TDD workflow.

Your responsibilities:
1. Review implementation AND test code
2. Refactor for clarity, maintainability
3. Ensure all tests still pass
4. Commit refactored code

May refactor any code in the codebase.""",
            tools=["Read", "Edit", "Bash", "Grep"],
            model="sonnet"
        )
    }
)
```

### 2.2 Agent Tool Restrictions

Each agent receives only the tools needed for its phase:

| Agent | Read | Write | Edit | Bash | Glob | Grep | Task |
|-------|------|-------|------|------|------|------|------|
| Test List | ✓ | ✓ | | | ✓ | ✓ | |
| Test | ✓ | ✓ | | ✓ | | | |
| Implementing | ✓ | ✓ | ✓ | ✓ | | | |
| Refactor | ✓ | | ✓ | ✓ | | ✓ | |

**Rationale:**
- **Test List Agent**: Plans only, no code execution
- **Test Agent**: Writes new files (tests), runs tests to verify failure
- **Implementing Agent**: Can create new files OR edit existing ones
- **Refactor Agent**: Edit only (no creating new files), can search for patterns

---

## 3. Handoff Implementation Strategies

### 3.1 Strategy A: Filesystem + State File (Recommended)

Use a `.tdd-state.json` file as the coordination mechanism:

```json
{
  "feature": "user-authentication",
  "current_cycle": 3,
  "current_phase": "green",
  "test_list_file": "tests/test-list-user-auth.md",
  "current_test": {
    "description": "User can log in with valid credentials",
    "test_file": "tests/test_user_login.py",
    "impl_file": "src/auth/login.py"
  },
  "completed_tests": [
    "User model exists with email and password_hash",
    "Password can be hashed and verified"
  ],
  "pending_tests": [
    "User can log out",
    "Invalid credentials return error"
  ],
  "last_phase_result": {
    "phase": "red",
    "success": true,
    "commit": "abc123",
    "errors": []
  }
}
```

**How it works:**

```
┌─────────────────────────────────────────────────────────────────────┐
│                    FILESYSTEM HANDOFF FLOW                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. Test List Agent                                                  │
│     ├─ Reads: feature request                                        │
│     ├─ Writes: test-list.md, .tdd-state.json                        │
│     └─ Commits: "plan: add test list for user-auth"                 │
│                           │                                          │
│                           ▼                                          │
│  2. Test Agent                                                       │
│     ├─ Reads: .tdd-state.json (gets current_test)                   │
│     ├─ Writes: test file                                            │
│     ├─ Runs: tests (verifies failure)                               │
│     ├─ Updates: .tdd-state.json (phase: "red" → complete)           │
│     └─ Commits: "test: add failing test for login"                  │
│                           │                                          │
│                           ▼                                          │
│  3. Implementing Agent                                               │
│     ├─ Reads: .tdd-state.json, test file                            │
│     ├─ Writes: implementation                                        │
│     ├─ Runs: tests (verifies passing)                               │
│     ├─ Updates: .tdd-state.json (phase: "green" → complete)         │
│     └─ Commits: "feat: implement login"                             │
│                           │                                          │
│                           ▼                                          │
│  4. Refactor Agent                                                   │
│     ├─ Reads: .tdd-state.json, test file, impl file                 │
│     ├─ Edits: code as needed                                        │
│     ├─ Runs: tests (verifies still passing)                         │
│     ├─ Updates: .tdd-state.json (cycle complete)                    │
│     └─ Commits: "refactor: clean up login implementation"           │
│                           │                                          │
│                           ▼                                          │
│  5. Back to Test List Agent (next cycle)                            │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Strategy B: Session Resumption

Pass the session ID between agents to maintain conversation context:

```python
async def run_tdd_cycle(feature_request: str) -> str:
    session_id = None

    # Phase 1: Test List Agent
    async for msg in query(
        prompt=f"[TEST LIST AGENT] Create test list for: {feature_request}",
        options=ClaudeAgentOptions(...)
    ):
        if msg.type == 'system' and msg.subtype == 'init':
            session_id = msg.session_id

    # Phase 2: Test Agent (resumes session)
    async for msg in query(
        prompt="[TEST AGENT] Write failing test for the first pending test",
        options=ClaudeAgentOptions(
            resume=session_id,  # Continues from previous context
            ...
        )
    ):
        pass

    # Phase 3: Implementing Agent (resumes session)
    async for msg in query(
        prompt="[IMPLEMENTING AGENT] Make the failing test pass",
        options=ClaudeAgentOptions(
            resume=session_id,
            ...
        )
    ):
        pass

    # Phase 4: Refactor Agent (resumes session)
    async for msg in query(
        prompt="[REFACTOR AGENT] Refactor the implementation",
        options=ClaudeAgentOptions(
            resume=session_id,
            ...
        )
    ):
        pass

    return session_id
```

**Trade-offs:**

| Aspect | Filesystem (Strategy A) | Session Resumption (Strategy B) |
|--------|------------------------|--------------------------------|
| Context isolation | ✓ Full isolation | ✗ Context accumulates |
| Context size | ✓ Small, focused | ✗ Grows each phase |
| Debugging | ✓ Inspect .tdd-state.json | ✗ Harder to trace |
| Parallelization | ✓ Possible for independent cycles | ✗ Sequential only |
| Failure recovery | ✓ Restart from state file | ✗ Must replay from session |
| Spec compliance | ✓ Matches "new session each time" | ✗ Same session |

### 3.3 Strategy C: Orchestrator with Subagent Invocation

A main orchestrator agent spawns subagents using the Task tool:

```python
orchestrator_prompt = """You are the TDD Orchestrator.

Your role is to coordinate the TDD workflow by invoking specialized agents:
1. test-list-agent: Creates/updates test list
2. test-agent: Writes failing tests (Red)
3. implementing-agent: Makes tests pass (Green)
4. refactor-agent: Improves code quality (Refactor)

For each TDD cycle:
1. Invoke test-list-agent to get the next test
2. Invoke test-agent to write the failing test
3. Invoke implementing-agent to make it pass
4. Invoke refactor-agent to clean up
5. Repeat until test-list-agent signals completion

Use the Task tool to invoke each agent with specific context."""

async for msg in query(
    prompt=f"{orchestrator_prompt}\n\nFeature request: {feature_request}",
    options=ClaudeAgentOptions(
        allowed_tools=["Task", "Read"],
        agents={
            "test-list-agent": AgentDefinition(...),
            "test-agent": AgentDefinition(...),
            "implementing-agent": AgentDefinition(...),
            "refactor-agent": AgentDefinition(...)
        }
    )
):
    print(msg)
```

**How subagent invocation works:**

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ORCHESTRATOR PATTERN                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │              ORCHESTRATOR AGENT                              │    │
│  │                                                              │    │
│  │  "Use the test-list-agent to create a test list for         │    │
│  │   user authentication"                                       │    │
│  │                                                              │    │
│  │        │                                                     │    │
│  │        ▼                                                     │    │
│  │  ┌──────────────────────────────────────────────────────┐   │    │
│  │  │ Task Tool Call                                       │   │    │
│  │  │ subagent_type: "test-list-agent"                     │   │    │
│  │  │ prompt: "Create test list for user auth feature"     │   │    │
│  │  └──────────────────────────────────────────────────────┘   │    │
│  │        │                                                     │    │
│  │        ▼                                                     │    │
│  │  ┌──────────────────────────────────────────────────────┐   │    │
│  │  │ SUBAGENT EXECUTION (isolated context)                │   │    │
│  │  │ - Reads feature requirements                         │   │    │
│  │  │ - Creates test-list.md                               │   │    │
│  │  │ - Returns: "Created test list with 5 tests"          │   │    │
│  │  └──────────────────────────────────────────────────────┘   │    │
│  │        │                                                     │    │
│  │        ▼                                                     │    │
│  │  Orchestrator receives result, decides next step            │    │
│  │                                                              │    │
│  │  "Now use the test-agent to write the first failing test"   │    │
│  │                                                              │    │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. Context Passing Mechanisms

### 4.1 What Context Each Agent Needs

| Agent | Input Context | Output |
|-------|---------------|--------|
| **Test List** | Feature request, existing test list (if any) | Updated test list, next test description |
| **Test** | Single test description | Test file path, test code |
| **Implementing** | Test file path, test description | Implementation file path |
| **Refactor** | Test file, impl file, codebase patterns | Refactored files |

### 4.2 Context Passing via Prompts

Each agent receives explicit context in its prompt:

```python
# Test Agent prompt construction
test_agent_prompt = f"""
## Context
You are implementing the Test Agent (Red Phase).

## Current Test to Implement
{current_test_description}

## Test List Reference
{test_list_content}

## Existing Test Structure
{existing_test_files}

## Task
Write a failing test for the described test case.
"""
```

### 4.3 Context Passing via MCP Tools

Create custom MCP tools for TDD state management:

```python
@tool(
    "get_tdd_state",
    "Read the current TDD workflow state",
    {}
)
async def get_tdd_state(args: dict) -> dict:
    with open(".tdd-state.json") as f:
        state = json.load(f)
    return {
        "content": [{
            "type": "text",
            "text": json.dumps(state, indent=2)
        }]
    }

@tool(
    "update_tdd_state",
    "Update the TDD workflow state after completing a phase",
    {
        "phase": "str - completed phase name",
        "success": "bool - whether phase succeeded",
        "commit": "str - commit hash if committed",
        "next_phase": "str - next phase to execute"
    }
)
async def update_tdd_state(args: dict) -> dict:
    # Read, update, write state
    ...
```

---

## 5. Implementation Patterns

### 5.1 Python Orchestrator (Full Example)

```python
#!/usr/bin/env python3
"""TDD Workflow Orchestrator using Claude Agent SDK"""

import asyncio
import json
from pathlib import Path
from claude_agent_sdk import query, ClaudeAgentOptions, AgentDefinition

class TDDOrchestrator:
    def __init__(self, project_root: str):
        self.project_root = Path(project_root)
        self.state_file = self.project_root / ".tdd-state.json"

    def load_state(self) -> dict:
        if self.state_file.exists():
            return json.loads(self.state_file.read_text())
        return {"current_phase": "plan", "cycle": 0}

    def save_state(self, state: dict):
        self.state_file.write_text(json.dumps(state, indent=2))

    async def run_agent(self, agent_name: str, prompt: str, tools: list[str]) -> str:
        """Run a single agent phase"""
        result = []

        async for msg in query(
            prompt=prompt,
            options=ClaudeAgentOptions(
                cwd=str(self.project_root),
                allowed_tools=tools,
                model="claude-sonnet-4-5"
            )
        ):
            if msg.type == 'assistant':
                result.append(msg.content)

        return "\n".join(result)

    async def run_test_list_agent(self, feature_request: str) -> dict:
        """Phase 1: Plan - Create/update test list"""
        state = self.load_state()

        prompt = f"""You are the Test List Agent.

Feature Request: {feature_request}

Current State: {json.dumps(state, indent=2)}

Tasks:
1. Read any existing test list
2. Create or update the test list
3. Select the next pending test
4. Update .tdd-state.json with the next test

Output the selected test description clearly."""

        await self.run_agent(
            "test-list-agent",
            prompt,
            ["Read", "Write", "Glob", "Grep"]
        )

        return self.load_state()

    async def run_test_agent(self) -> dict:
        """Phase 2: Red - Write failing test"""
        state = self.load_state()

        prompt = f"""You are the Test Agent (Red Phase).

Current Test: {state.get('current_test', {}).get('description', 'None')}

State: {json.dumps(state, indent=2)}

Tasks:
1. Write a failing test for the current test case
2. Run tests to verify your new test fails
3. Commit the failing test
4. Update .tdd-state.json with phase completion"""

        await self.run_agent(
            "test-agent",
            prompt,
            ["Read", "Write", "Bash"]
        )

        return self.load_state()

    async def run_implementing_agent(self) -> dict:
        """Phase 3: Green - Make test pass"""
        state = self.load_state()

        prompt = f"""You are the Implementing Agent (Green Phase).

Current Test: {state.get('current_test', {})}

State: {json.dumps(state, indent=2)}

Tasks:
1. Read the failing test
2. Write MINIMUM code to make it pass
3. Run all tests to verify they pass
4. Commit the implementation
5. Update .tdd-state.json"""

        await self.run_agent(
            "implementing-agent",
            prompt,
            ["Read", "Write", "Edit", "Bash"]
        )

        return self.load_state()

    async def run_refactor_agent(self) -> dict:
        """Phase 4: Refactor - Improve code"""
        state = self.load_state()

        prompt = f"""You are the Refactor Agent.

Current Test: {state.get('current_test', {})}

State: {json.dumps(state, indent=2)}

Tasks:
1. Review the test and implementation
2. Refactor for clarity and maintainability
3. Run tests to ensure they still pass
4. Commit refactored code
5. Update .tdd-state.json to complete cycle"""

        await self.run_agent(
            "refactor-agent",
            prompt,
            ["Read", "Edit", "Bash", "Grep"]
        )

        return self.load_state()

    async def run_cycle(self, feature_request: str):
        """Run one complete TDD cycle"""
        print("📋 Phase 1: Planning (Test List Agent)")
        await self.run_test_list_agent(feature_request)

        print("🔴 Phase 2: Red (Test Agent)")
        await self.run_test_agent()

        print("🟢 Phase 3: Green (Implementing Agent)")
        await self.run_implementing_agent()

        print("🔵 Phase 4: Refactor (Refactor Agent)")
        await self.run_refactor_agent()

        print("✅ Cycle complete!")
        return self.load_state()

# Usage
async def main():
    orchestrator = TDDOrchestrator("/path/to/project")
    await orchestrator.run_cycle("Add user authentication with login/logout")

if __name__ == "__main__":
    asyncio.run(main())
```

### 5.2 Shell Script Orchestrator (Simpler Alternative)

```bash
#!/bin/bash
# tdd-orchestrator.sh - Simple shell-based TDD workflow

PROJECT_ROOT="${1:-.}"
FEATURE="${2:-}"

if [ -z "$FEATURE" ]; then
    echo "Usage: $0 <project_root> <feature_request>"
    exit 1
fi

STATE_FILE="$PROJECT_ROOT/.tdd-state.json"

# Phase 1: Test List Agent
echo "📋 Running Test List Agent..."
claude --print --dangerously-skip-permissions \
    --prompt "You are the Test List Agent. Create a test list for: $FEATURE" \
    --cwd "$PROJECT_ROOT" \
    --allowedTools "Read,Write,Glob,Grep"

# Phase 2: Test Agent
echo "🔴 Running Test Agent..."
claude --print --dangerously-skip-permissions \
    --prompt "You are the Test Agent. Read .tdd-state.json and write a failing test for the current test case." \
    --cwd "$PROJECT_ROOT" \
    --allowedTools "Read,Write,Bash"

# Phase 3: Implementing Agent
echo "🟢 Running Implementing Agent..."
claude --print --dangerously-skip-permissions \
    --prompt "You are the Implementing Agent. Read .tdd-state.json and make the failing test pass." \
    --cwd "$PROJECT_ROOT" \
    --allowedTools "Read,Write,Edit,Bash"

# Phase 4: Refactor Agent
echo "🔵 Running Refactor Agent..."
claude --print --dangerously-skip-permissions \
    --prompt "You are the Refactor Agent. Read .tdd-state.json and refactor the code." \
    --cwd "$PROJECT_ROOT" \
    --allowedTools "Read,Edit,Bash,Grep"

echo "✅ TDD cycle complete!"
```

---

## 6. Error Handling in SDK Context

### 6.1 Detecting Failures

The SDK provides message types for error detection:

```python
async for msg in query(prompt, options):
    if msg.type == 'error':
        # Handle SDK/API error
        handle_error(msg)

    if msg.type == 'tool_result' and msg.tool_name == 'Bash':
        # Check test execution results
        if 'FAILED' in msg.result or msg.exit_code != 0:
            handle_test_failure(msg)
```

### 6.2 Recovery Strategies

| Failure Type | SDK Recovery Approach |
|--------------|----------------------|
| Test doesn't compile | Re-run test-agent with error context in prompt |
| Test passes immediately | Re-run test-agent with instruction to check assertions |
| Implementation breaks other tests | Fork session, try alternative approach |
| Refactoring breaks tests | Rollback via git, re-run refactor-agent |

### 6.3 Implementing Retries

```python
async def run_with_retry(agent_func, max_retries=3):
    for attempt in range(max_retries):
        try:
            state = await agent_func()
            if state.get('last_phase_result', {}).get('success'):
                return state

            # Phase failed, add error context for next attempt
            error_context = state.get('last_phase_result', {}).get('errors', [])
            # Retry with error context...

        except Exception as e:
            if attempt == max_retries - 1:
                raise
            await asyncio.sleep(2 ** attempt)  # Exponential backoff
```

---

## 7. Comparison: SDK vs Other Approaches

| Criterion | Claude Agent SDK | File-Only | Git-Native | External Queue |
|-----------|-----------------|-----------|------------|----------------|
| **Complexity** | Medium | Low | Low | High |
| **Dependencies** | SDK package | None | Git only | Redis/RabbitMQ |
| **Claude Native** | ✓ Full integration | Partial | No | No |
| **Context Passing** | ✓ Built-in | Manual | Manual | Manual |
| **Error Handling** | ✓ SDK events | Manual | Manual | Queue features |
| **Parallelization** | ✓ Subagents | Manual | Manual | ✓ Native |
| **Observability** | ✓ SDK logs | File inspection | Git history | Queue monitoring |
| **Portability** | Python/TS required | Universal | Universal | Platform-specific |

---

## 8. Recommendations

### 8.1 For This Project (RedGreenRefactor)

**Recommended approach: Hybrid Filesystem + SDK**

1. **Use Claude Agent SDK** for agent invocation and session management
2. **Use `.tdd-state.json`** for state persistence and handoff data
3. **Use git commits** for atomic phase completion markers
4. **Use tool restrictions** to enforce agent boundaries

### 8.2 Implementation Priority

1. **Start simple**: Python orchestrator with query() for each agent
2. **Add state management**: .tdd-state.json for coordination
3. **Add error handling**: Retry logic with error context
4. **Optimize later**: Subagent definitions, custom MCP tools

### 8.3 Key Success Factors

- **Clear state file schema**: Define .tdd-state.json structure upfront
- **Explicit prompts**: Each agent gets full context in its prompt
- **Tool restrictions**: Prevent agents from exceeding their scope
- **Commit discipline**: Each phase commits before handoff
- **Error detection**: Parse test output to detect failures early

---

## 9. Open Questions for Implementation

1. **Session ID persistence**: Should session IDs be stored in state file for potential resumption?
2. **Parallel cycles**: Can multiple TDD cycles run in parallel for different features?
3. **Human approval gates**: Should there be optional pause points for human review?
4. **Rollback granularity**: Should rollback be to last commit or last known-good state?
5. **Test framework detection**: Should orchestrator auto-detect pytest/jest/etc.?

---

## Appendix A: SDK Message Types Reference

```python
# Message types relevant to TDD orchestration
MESSAGE_TYPES = {
    "system": {
        "init": "Session started, contains session_id",
        "complete": "Agent finished processing"
    },
    "assistant": {
        "text": "Agent's response text",
        "tool_use": "Agent invoking a tool"
    },
    "tool_result": {
        "success": "Tool executed successfully",
        "error": "Tool execution failed"
    },
    "error": {
        "api_error": "API-level error",
        "rate_limit": "Rate limit exceeded"
    }
}
```

## Appendix B: MCP Tools for TDD

```python
# Suggested custom MCP tools for TDD workflow
TDD_MCP_TOOLS = [
    "get_tdd_state",      # Read current workflow state
    "update_tdd_state",   # Update state after phase completion
    "get_next_test",      # Get next pending test from list
    "mark_test_complete", # Mark test as completed
    "run_test_suite",     # Run tests with structured output
    "check_test_failure", # Verify a specific test fails
    "check_all_pass",     # Verify all tests pass
    "git_commit_phase",   # Commit with TDD-formatted message
]
```
