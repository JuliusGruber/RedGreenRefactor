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
│    │ List    │  Git   │ Agent   │  Git   │ Agent   │  Git   │ Agent   │
│    │ Agent   │ Notes  │         │ Notes  │         │ Notes  │         │
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

## 3. Chosen Handoff Approach

> **Decision**: The handoff mechanism uses **Anthropic SDK + Git Commits + Git Notes**.
>
> See [spec-handoffs.md](spec-handoffs.md) for the full specification of the chosen approach.
> See [research-java-sdk.md](research-java-sdk.md) for detailed Java SDK implementation research.

---

## 4. Context Passing Mechanisms

> **Note**: State management uses **Git Notes** for non-intrusive metadata storage. See [research-java-sdk.md](research-java-sdk.md) for detailed Git Notes implementation.

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

### 4.3 Context Passing via Git Notes

Create utilities for reading and writing Git Notes:

```python
import subprocess
import json

TDD_NOTES_REF = "refs/notes/tdd-handoffs"

def read_handoff_state(commit: str = "HEAD") -> dict:
    """Read handoff state from Git Notes"""
    try:
        result = subprocess.run(
            ["git", "notes", f"--ref={TDD_NOTES_REF}", "show", commit],
            capture_output=True, text=True, check=True
        )
        return json.loads(result.stdout)
    except subprocess.CalledProcessError:
        return {"phase": "PLAN", "cycle": 0}

def write_handoff_state(state: dict, commit: str = "HEAD") -> None:
    """Write handoff state to Git Notes"""
    state_json = json.dumps(state, indent=2)
    subprocess.run(
        ["git", "notes", f"--ref={TDD_NOTES_REF}", "add", "-f", "-m", state_json, commit],
        check=True
    )

def get_latest_handoff_commit() -> str:
    """Find the most recent commit with a handoff note"""
    result = subprocess.run(
        ["git", "notes", f"--ref={TDD_NOTES_REF}", "list"],
        capture_output=True, text=True
    )
    if result.stdout.strip():
        # Format: <note-sha> <commit-sha>
        first_line = result.stdout.strip().split('\n')[0]
        return first_line.split()[1]
    return "HEAD"
```

---

## 5. Implementation Patterns

### 5.1 Python Orchestrator with Git Notes

```python
#!/usr/bin/env python3
"""TDD Workflow Orchestrator using Claude Agent SDK with Git Notes"""

import asyncio
import json
import subprocess
from pathlib import Path
from datetime import datetime
from claude_agent_sdk import query, ClaudeAgentOptions, AgentDefinition

TDD_NOTES_REF = "refs/notes/tdd-handoffs"

class GitNotesManager:
    """Manages TDD handoff state via Git Notes"""

    def __init__(self, repo_path: Path):
        self.repo_path = repo_path

    def _run_git(self, *args, check=True) -> subprocess.CompletedProcess:
        return subprocess.run(
            ["git", *args],
            cwd=self.repo_path,
            capture_output=True,
            text=True,
            check=check
        )

    def read_handoff(self, commit: str = "HEAD") -> dict:
        """Read handoff state from Git Notes"""
        try:
            result = self._run_git("notes", f"--ref={TDD_NOTES_REF}", "show", commit)
            return json.loads(result.stdout)
        except (subprocess.CalledProcessError, json.JSONDecodeError):
            return {"phase": "PLAN", "cycle": 1, "pending_tests": [], "completed_tests": []}

    def write_handoff(self, state: dict, commit: str = "HEAD") -> None:
        """Write handoff state to Git Notes"""
        state["timestamp"] = datetime.utcnow().isoformat() + "Z"
        state_json = json.dumps(state, indent=2)
        self._run_git("notes", f"--ref={TDD_NOTES_REF}", "add", "-f", "-m", state_json, commit)

    def get_head_commit(self) -> str:
        """Get the current HEAD commit SHA"""
        result = self._run_git("rev-parse", "HEAD")
        return result.stdout.strip()

    def commit_changes(self, message: str) -> str:
        """Stage all changes and commit, return commit SHA"""
        self._run_git("add", "-A")
        self._run_git("commit", "-m", message, check=False)
        return self.get_head_commit()


class TDDOrchestrator:
    def __init__(self, project_root: str):
        self.project_root = Path(project_root)
        self.git_notes = GitNotesManager(self.project_root)

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
        state = self.git_notes.read_handoff()

        prompt = f"""You are the Test List Agent.

Feature Request: {feature_request}

Current State: {json.dumps(state, indent=2)}

Tasks:
1. Read any existing test list
2. Create or update the test list file (test-list.md)
3. Select the next pending test
4. Commit the test list

Output the selected test description clearly."""

        await self.run_agent(
            "test-list-agent",
            prompt,
            ["Read", "Write", "Glob", "Grep"]
        )

        # Commit and update state
        commit_sha = self.git_notes.commit_changes("plan: update test list")
        state["phase"] = "PLAN"
        state["next_phase"] = "RED"
        self.git_notes.write_handoff(state, commit_sha)

        return state

    async def run_test_agent(self) -> dict:
        """Phase 2: Red - Write failing test"""
        state = self.git_notes.read_handoff()

        prompt = f"""You are the Test Agent (Red Phase).

Current Test: {state.get('current_test', {}).get('description', 'None')}

State: {json.dumps(state, indent=2)}

Tasks:
1. Write a failing test for the current test case
2. Run tests to verify your new test fails
3. Commit the failing test"""

        await self.run_agent(
            "test-agent",
            prompt,
            ["Read", "Write", "Bash"]
        )

        # Commit and update state
        commit_sha = self.git_notes.commit_changes("test: add failing test")
        state["phase"] = "RED"
        state["next_phase"] = "GREEN"
        state["test_result"] = "FAIL"
        self.git_notes.write_handoff(state, commit_sha)

        return state

    async def run_implementing_agent(self) -> dict:
        """Phase 3: Green - Make test pass"""
        state = self.git_notes.read_handoff()

        prompt = f"""You are the Implementing Agent (Green Phase).

Current Test: {state.get('current_test', {})}

State: {json.dumps(state, indent=2)}

Tasks:
1. Read the failing test
2. Write MINIMUM code to make it pass
3. Run all tests to verify they pass
4. Commit the implementation"""

        await self.run_agent(
            "implementing-agent",
            prompt,
            ["Read", "Write", "Edit", "Bash"]
        )

        # Commit and update state
        commit_sha = self.git_notes.commit_changes("feat: implement to pass test")
        state["phase"] = "GREEN"
        state["next_phase"] = "REFACTOR"
        state["test_result"] = "PASS"
        self.git_notes.write_handoff(state, commit_sha)

        return state

    async def run_refactor_agent(self) -> dict:
        """Phase 4: Refactor - Improve code"""
        state = self.git_notes.read_handoff()

        prompt = f"""You are the Refactor Agent.

Current Test: {state.get('current_test', {})}

State: {json.dumps(state, indent=2)}

Tasks:
1. Review the test and implementation
2. Refactor for clarity and maintainability
3. Run tests to ensure they still pass
4. Commit refactored code"""

        await self.run_agent(
            "refactor-agent",
            prompt,
            ["Read", "Edit", "Bash", "Grep"]
        )

        # Commit and update state
        commit_sha = self.git_notes.commit_changes("refactor: improve code quality")
        state["phase"] = "REFACTOR"
        state["next_phase"] = "PLAN"
        if state.get("current_test"):
            state.setdefault("completed_tests", []).append(
                state["current_test"].get("description", "")
            )
        self.git_notes.write_handoff(state, commit_sha)

        return state

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
        return self.git_notes.read_handoff()

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
# tdd-orchestrator.sh - Simple shell-based TDD workflow with Git Notes

PROJECT_ROOT="${1:-.}"
FEATURE="${2:-}"
TDD_NOTES_REF="refs/notes/tdd-handoffs"

if [ -z "$FEATURE" ]; then
    echo "Usage: $0 <project_root> <feature_request>"
    exit 1
fi

cd "$PROJECT_ROOT" || exit 1

# Helper function to read handoff state
read_handoff() {
    git notes --ref="$TDD_NOTES_REF" show HEAD 2>/dev/null || echo '{"phase":"PLAN","cycle":1}'
}

# Helper function to write handoff state
write_handoff() {
    local state="$1"
    git notes --ref="$TDD_NOTES_REF" add -f -m "$state" HEAD
}

# Phase 1: Test List Agent
echo "📋 Running Test List Agent..."
STATE=$(read_handoff)
claude --print --dangerously-skip-permissions \
    --prompt "You are the Test List Agent. Create a test list for: $FEATURE. Current state: $STATE" \
    --cwd "$PROJECT_ROOT" \
    --allowedTools "Read,Write,Glob,Grep"
git add -A && git commit -m "plan: update test list"
write_handoff '{"phase":"PLAN","next_phase":"RED"}'

# Phase 2: Test Agent
echo "🔴 Running Test Agent..."
STATE=$(read_handoff)
claude --print --dangerously-skip-permissions \
    --prompt "You are the Test Agent. Write a failing test. State: $STATE" \
    --cwd "$PROJECT_ROOT" \
    --allowedTools "Read,Write,Bash"
git add -A && git commit -m "test: add failing test"
write_handoff '{"phase":"RED","next_phase":"GREEN","test_result":"FAIL"}'

# Phase 3: Implementing Agent
echo "🟢 Running Implementing Agent..."
STATE=$(read_handoff)
claude --print --dangerously-skip-permissions \
    --prompt "You are the Implementing Agent. Make the failing test pass. State: $STATE" \
    --cwd "$PROJECT_ROOT" \
    --allowedTools "Read,Write,Edit,Bash"
git add -A && git commit -m "feat: implement to pass test"
write_handoff '{"phase":"GREEN","next_phase":"REFACTOR","test_result":"PASS"}'

# Phase 4: Refactor Agent
echo "🔵 Running Refactor Agent..."
STATE=$(read_handoff)
claude --print --dangerously-skip-permissions \
    --prompt "You are the Refactor Agent. Refactor the code. State: $STATE" \
    --cwd "$PROJECT_ROOT" \
    --allowedTools "Read,Edit,Bash,Grep"
git add -A && git commit -m "refactor: improve code quality"
write_handoff '{"phase":"REFACTOR","next_phase":"PLAN"}'

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

**Chosen approach: Anthropic SDK + Git Commits + Git Notes**

1. **Use Anthropic SDK** (Java or Python) for agent invocation
2. **Use Git Notes** for state persistence and handoff data
3. **Use git commits** for atomic phase completion markers
4. **Use tool restrictions** to enforce agent boundaries

See [spec-handoffs.md](spec-handoffs.md) for the full specification.

### 8.2 Implementation Priority

1. **Start simple**: Orchestrator with SDK for each agent
2. **Add state management**: Git Notes for coordination
3. **Add error handling**: Retry logic with error context
4. **Optimize later**: Streaming, async execution

### 8.3 Key Success Factors

- **Clear handoff state schema**: Define Git Notes structure upfront
- **Explicit prompts**: Each agent gets full context in its prompt
- **Tool restrictions**: Prevent agents from exceeding their scope
- **Commit discipline**: Each phase commits before handoff
- **Error detection**: Parse test output to detect failures early

---

## 9. Open Questions for Implementation

1. **Session ID persistence**: Should session IDs be stored in Git Notes for potential resumption?
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

## Appendix B: Git Notes Helper Tools for TDD

```python
# Suggested custom tools for TDD workflow with Git Notes
TDD_GIT_TOOLS = [
    "read_handoff_state",   # Read workflow state from Git Notes
    "write_handoff_state",  # Write state to Git Notes after phase completion
    "get_next_test",        # Get next pending test from list
    "mark_test_complete",   # Mark test as completed in Git Notes
    "run_test_suite",       # Run tests with structured output
    "check_test_failure",   # Verify a specific test fails
    "check_all_pass",       # Verify all tests pass
    "git_commit_phase",     # Commit with TDD-formatted message
    "git_notes_push",       # Push notes to remote repository
]
```
