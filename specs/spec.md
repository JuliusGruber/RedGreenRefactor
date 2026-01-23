# RedGreenRefactor Agent Workflow Specification

## Main Idea

Four **independent Claude Code sessions** collaborate in a Test-Driven Development pipeline. Each session has a specialized role and hands off to the next session in sequence, following the Red-Green-Refactor cycle.

**Key architectural points:**
- Sessions are fully independent (like 4 separate terminal windows running `claude`)
- No shared memory or context between sessions
- The git repository serves as the shared state
- Handoff mechanism coordinates transitions between sessions (see [Handoffs Specification](spec-handoffs.md))

> **Terminology**: Throughout this spec, "Agent" refers to a Claude Code session with a specific role prompt (e.g., "Test List Agent" = a Claude Code session prompted to perform the planning role).

## Agent Roles

> **Tool Access**: All agents have access to all tools (Read, Write, Edit, Bash, Glob, Grep). Agents self-regulate based on their system prompts and role definitions. Full tool access allows agents to handle edge cases and unexpected situations.

### 1. Test List Agent (Planning)
- Receives a feature request
- Analyzes requirements and breaks them down
- **Writes a comprehensive test list** (stored as `test-list.md` in project root, using markdown checkboxes)
- Marks tests as pending/completed in the list
- **Commits** the test list with message prefix `plan:`
- **Handoff →** Outputs JSON with next test selection:
  ```json
  {
    "currentTest": {
      "description": "test description",
      "testFile": "src/test/java/...",
      "implFile": "src/main/java/..."
    }
  }
  ```
  Or when all tests are complete: `{"currentTest": null}`
- The orchestrator determines feature completion by checking if all tests in `test-list.md` are marked `[x]`

### 2. Test Agent (Red Phase)
- Receives **one test** from the test list (the next pending test)
- Writes a failing test for that **single test case**
- Ensures the test is focused and well-structured
- **Commits** the failing test with message prefix `test:`
- **Handoff →** Implementing Agent

### 3. Implementing Agent (Green Phase)
- Receives the single failing test
- Writes minimum code to make **that test** pass
- Focuses on functionality, not perfection
- **Commits** the passing implementation with message prefix `feat:` or `fix:`
- **Handoff →** Refactor Agent

### 4. Refactor Agent (Refactor Phase)
- Reviews the implementation
- Refactors **both test and implementation code** while keeping tests green
- May refactor any code in the codebase, not just the current cycle's changes
- Improves code quality, readability, and maintainability
- **Commits** the refactored code with message prefix `refactor:` (or empty commit with `git commit --allow-empty -m "refactor: no changes needed"`)
- **Handoff →** Test List Agent (to get the next test from the list)

## Phase Transitions

The **orchestrator controls the phase sequence**. The fixed order is:

```
PLAN → RED → GREEN → REFACTOR → PLAN (loop) or COMPLETE
```

- The orchestrator sets `nextPhase` after each phase completes, following the fixed sequence
- The orchestrator determines feature completion by checking if all tests in `test-list.md` are marked `[x]`
- Agents do not need to specify `nextPhase`; the orchestrator handles all transitions

## The TDD Process Philosophy

Although the three steps—often summarized as **Red - Green - Refactor**—are the heart of the process, there's also a vital initial step where we write out a list of test cases first. We then pick one of these tests, apply red-green-refactor to it, and once we're done pick the next.

**Key principles:**
- **One test at a time**: Each agent receives exactly ONE test from the list, not the entire list
- **Sequencing tests properly is a skill**: We want to pick tests that drive us quickly to the salient points in the design
- **The test list is dynamic**: During the process, we should add more tests to our list as they occur to us
- **Iterative refinement**: Each completed cycle informs the next test selection

This means the **Test List Agent** runs not only at the start, but also **after each red-green-refactor cycle** to:
1. Review the current test list
2. Mark the completed test as done
3. Add any new tests discovered during implementation
4. **Select the next pending test** from the list
5. Hand off **that single test** to the Test Agent for the next cycle

## Test Execution Responsibilities

Each agent has specific test execution responsibilities to ensure the TDD cycle integrity:

| Agent | Runs Tests | Expected Result |
|-------|------------|-----------------|
| **Test List Agent** | No | — |
| **Test Agent** | Yes, all tests | All pass **except** the new test (which must fail) |
| **Implementing Agent** | Yes, all tests | All tests pass (including the new one) |
| **Refactor Agent** | Yes, all tests | All tests pass (unchanged after refactoring) |

**Key principle**: All tests created since the start of the workflow must pass (except during the Red phase for the new test).

## Workflow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    RED-GREEN-REFACTOR CYCLE                         │
└─────────────────────────────────────────────────────────────────────┘

    ┌──────────────┐
    │   FEATURE    │
    │   REQUEST    │
    └──────┬───────┘
           │
           ▼
┌─────────────────────┐
│  TEST LIST AGENT    │  ← Planning
│  (Creates test list)│
└──────────┬──────────┘
           │
           ▼
    [COMMIT 1: Plan]
           │
           ▼
┌─────────────────────┐
│    TEST AGENT       │  ← 🔴 RED
│ (Writes failing     │
│      tests)         │
└──────────┬──────────┘
           │
           ▼
     [COMMIT 2: Red]
           │
           ▼
┌─────────────────────┐
│ IMPLEMENTING AGENT  │  ← 🟢 GREEN
│ (Makes tests pass)  │
└──────────┬──────────┘
           │
           ▼
    [COMMIT 3: Green]
           │
           ▼
┌─────────────────────┐
│  REFACTOR AGENT     │  ← 🔵 REFACTOR
│ (Improves code)     │
└──────────┬──────────┘
           │
           ▼
  [COMMIT 4: Refactor]
           │
           ▼
    ┌──────────────┐
    │   COMPLETE   │
    └──────────────┘
```

## Cycle Flow

```
    ┌──────────────────────────────────────────────────────────────┐
    │                                                              │
    │   📋 PLAN ───► 🔴 RED ───► 🟢 GREEN ───► 🔵 REFACTOR        │
    │      │           │            │              │               │
    │   Test List    Failing     Passing       Improved           │
    │    Agent        Tests       Tests          Code             │
    │      │           │            │              │               │
    │      ▼           ▼            ▼              ▼               │
    │   COMMIT 1   COMMIT 2     COMMIT 3      COMMIT 4            │
    │                                                              │
    └──────────────────────────────────────────────────────────────┘
```

## Iterative TDD Cycle Diagram

The following diagram shows how the Test List Agent writes a test list and passes tests **one by one** to subsequent agents:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              ONE TEST AT A TIME: ITERATIVE TDD CYCLE                        │
└─────────────────────────────────────────────────────────────────────────────┘

                         ┌──────────────┐
                         │   FEATURE    │
                         │   REQUEST    │
                         └──────┬───────┘
                                │
                                ▼
              ┌─────────────────────────────────────┐
              │         TEST LIST AGENT             │
              │  ┌───────────────────────────────┐  │
              │  │ ✓ Test case 1 (completed)     │  │
              │  │ ► Test case 2 (next)  ◄───────│──│── PASSES THIS ONE TEST
              │  │ □ Test case 3 (pending)       │  │
              │  │ □ ...more tests as discovered │  │
              │  └───────────────────────────────┘  │
              │                                     │
              │  ► Writes/updates the test list     │
              │  ► Selects NEXT PENDING test        │
              │  ► Passes ONE test to Test Agent    │
              └──────────────────┬──────────────────┘
                                 │
                          (next test)
                                 │
                                 ▼
                          [COMMIT: Plan]
                                 │
         ┌───────────────────────┼───────────────────────┐
         │                       ▼                       │
         │         ┌─────────────────────────┐           │
         │         │      TEST AGENT         │           │
         │         │    🔴 RED PHASE         │           │
         │         │ Receives: ONE test      │           │
         │         │ (Write failing test)    │           │
         │         └───────────┬─────────────┘           │
         │                     │                         │
         │                     ▼                         │
         │              [COMMIT: Red]                    │
         │                     │                         │
         │                     ▼                         │
         │         ┌─────────────────────────┐           │
         │         │  IMPLEMENTING AGENT     │           │
         │         │    🟢 GREEN PHASE       │           │
         │         │  (Make ONE test pass)   │           │
         │         └───────────┬─────────────┘           │
         │                     │                         │
         │                     ▼                         │
         │             [COMMIT: Green]                   │
         │                     │                         │
         │                     ▼                         │
         │         ┌─────────────────────────┐           │
         │         │    REFACTOR AGENT       │           │
         │         │   🔵 REFACTOR PHASE     │           │
         │         │   (Improve code)        │           │
         │         └───────────┬─────────────┘           │
         │                     │                         │
         │                     ▼                         │
         │           [COMMIT: Refactor]                  │
         │                     │                         │
         │                     ▼                         │
         │            ┌───────────────────┐               │
         │            │  TEST LIST AGENT  │               │
         │            │  Feature complete?│── Yes ─► DONE │
         │            └────────┬──────────┘               │
         │                     │                          │
         │                     No                         │
         │                     │                          │
         └─────────────────────┘                          │
                                                          │
                 LOOP BACK TO TEST LIST AGENT             │
                 (Mark test done, update list,            │
                  decide if complete or get NEXT test)    │
                                                         │
─────────────────────────────────────────────────────────┘
```

### The Iterative Process (One Test at a Time)

```
    ╔═══════════════════════════════════════════════════════════════════╗
    ║                                                                   ║
    ║   📋 PLAN ──► 🔴 RED ──► 🟢 GREEN ──► 🔵 REFACTOR ──┐            ║
    ║      ▲         (1 test)   (1 test)     (1 test)     │            ║
    ║      │                                              │             ║
    ║      │         ┌────────────────────────┐           │             ║
    ║      └─────────│  TEST LIST AGENT      │◄──────────┘             ║
    ║                │  Mark done, decide:   │                          ║
    ║                │  Complete? or NEXT?   │                          ║
    ║                └────────────────────────┘                         ║
    ║                                                                   ║
    ╚═══════════════════════════════════════════════════════════════════╝
```

## Test Framework Auto-Detection

The orchestrator automatically detects the test framework from project files (first match wins):

| Project File | Test Command |
|--------------|--------------|
| `pom.xml` with JUnit | `mvn test` |
| `build.gradle` or `build.gradle.kts` | `./gradlew test` |
| `package.json` with test script | `npm test` |
| `pytest.ini`, `pyproject.toml`, or `setup.py` | `pytest` |

If no framework is detected, the orchestrator aborts with a clear error message.

## Test List Format

The Test List Agent writes and maintains `test-list.md` in the project root. Each test in the list has a status:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         TEST LIST EXAMPLE                           │
├─────────────────────────────────────────────────────────────────────┤
│  Status │ Test Description                                          │
├─────────┼───────────────────────────────────────────────────────────┤
│   [x]   │ Returns empty array for empty input                       │
│   [x]   │ Returns single element for single-item array              │
│   [ ]   │ Sorts two elements in ascending order        ◄── NEXT     │
│   [ ]   │ Handles duplicate values                                  │
│   [ ]   │ Sorts negative numbers correctly                          │
│   [ ]   │ (new tests added as discovered during implementation)     │
└─────────────────────────────────────────────────────────────────────┘
```

**Handoff mechanism:**
- Test List Agent selects the **first unchecked** `[ ]` test
- Passes **only that one test** to the Test Agent
- After the cycle completes, marks it as `[x]` and selects the next

## Commit Structure

Each TDD cycle produces **four commits**, one from each agent:

```
┌─────────────────────────────────────────────────────────────────────┐
│                     COMMITS PER TDD CYCLE                           │
├─────────────────────────────────────────────────────────────────────┤
│  1. 📋 PLAN COMMIT     │  Test List Agent commits test list         │
│  2. 🔴 RED COMMIT      │  Test Agent commits failing tests          │
│  3. 🟢 GREEN COMMIT    │  Implementing Agent commits passing code   │
│  4. 🔵 REFACTOR COMMIT │  Refactor Agent commits refactored code    │
└─────────────────────────────────────────────────────────────────────┘
```

This ensures:
- **Atomic changes**: Each commit represents a single phase
- **Git history clarity**: Easy to trace the evolution of features
- **Rollback capability**: Can revert to any phase if needed
- **Code review**: Each phase can be reviewed independently

## Benefits

- **Separation of Concerns**: Each agent focuses on one task
- **Quality Assurance**: Tests written before implementation
- **Clean Code**: Dedicated refactoring phase ensures maintainability
- **Traceability**: Clear handoffs between phases

## Command-Line Interface

The orchestrator provides the following CLI commands:

| Command | Description |
|---------|-------------|
| `run <feature-request>` | Run full TDD workflow for a feature |
| `resume` | Resume from last handoff state |
| `status` | Show current workflow state |
| `history` | Show handoff history |
| `rollback <commit>` | Rollback to specific state |

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ANTHROPIC_API_KEY` | API key (required) | — |
| `TDD_PROJECT_ROOT` | Project directory | Current directory |
| `TDD_MAX_RETRIES` | Retry limit per phase | 3 |
| `TDD_MODEL` | Claude model to use | `claude-opus-4-5-20251101` |

### Config File

Optional `tdd.properties` file in project root (standard Java properties format):

| Property | Description | Default |
|----------|-------------|---------|
| `bash.timeout` | Bash command timeout in seconds | 120 |
| `test.command` | Override auto-detected test command | (auto-detect) |

## Model Configuration

- **Default model**: `claude-opus-4-5-20251101` (Claude Opus 4.5)
- **No fallback strategy**: If the configured model is unavailable, the orchestrator aborts with a clear error message rather than silently falling back to a different model
- **Rationale**: TDD quality depends on consistent model behavior; silent fallback may degrade results

## Implementation Decision: Java SDK

After evaluating available options for the orchestrator implementation, **we have decided to use the Anthropic Java SDK** with Git Notes for agent handoffs.

**Key reasons for this decision:**
- Type safety with compile-time checking for agent orchestration
- Enterprise maturity with proven concurrency and error handling
- Git Notes provide non-intrusive, version-controlled handoff state
- No external dependencies beyond Git itself

For full implementation details, see [Java SDK Research](research-java-sdk.md).

**Note:** The Python SDK approach documented in [Python SDK Handoffs Research](research-sdk-handoffs-python.md) is retained as a Python implementation reference using the same Git Notes architecture.

## Related Specifications

- [Handoffs Specification](spec-handoffs.md) - Agent context, information access, and handoff mechanisms
- [Error Handling Specification](spec-error-handling.md) - Failure scenarios and recovery strategies
- [Java SDK Research](research-java-sdk.md) - Chosen implementation approach using Anthropic Java SDK with Git Notes
