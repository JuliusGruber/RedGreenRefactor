# RedGreenRefactor Agent Workflow Specification

## Main Idea

Four sessions of the same coding agent collaborate in a Test-Driven Development pipeline. Each agent has a specialized role and hands off to the next agent in sequence, following the Red-Green-Refactor cycle.

## Agent Roles

### 1. Test List Agent (Planning)
- Receives a feature request
- Analyzes requirements and breaks them down
- **Writes a comprehensive test list** (stored as a file or document)
- Marks tests as pending/completed in the list
- **Decides when the feature is complete** (not just "no more tests")
- **Commits** the test list
- **Handoff →** Passes the **next pending test** to the Test Agent, or signals completion

### 2. Test Agent (Red Phase)
- Receives **one test** from the test list (the next pending test)
- Writes a failing test for that **single test case**
- Ensures the test is focused and well-structured
- **Commits** the failing test
- **Handoff →** Implementing Agent

### 3. Implementing Agent (Green Phase)
- Receives the single failing test
- Writes minimum code to make **that test** pass
- Focuses on functionality, not perfection
- **Commits** the passing implementation
- **Handoff →** Review Agent

### 4. Review Agent (Refactor Phase)
- Reviews the implementation
- Refactors code while keeping tests green
- Improves code quality, readability, and maintainability
- **Commits** the refactored code
- **Handoff →** Test List Agent (to get the next test from the list)

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
| **Review Agent** | Yes, all tests | All tests pass (unchanged after refactoring) |

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
│   REVIEW AGENT      │  ← 🔵 REFACTOR
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
         │         │    REVIEW AGENT         │           │
         │         │   🔵 REFACTOR PHASE     │           │
         │         │   (Improve code)        │           │
         │         └───────────┬─────────────┘           │
         │                     │                         │
         │                     ▼                         │
         │           [COMMIT: Refactor]                  │
         │                     │                         │
         │                     ▼                         │
         │            ┌────────────────┐                 │
         │            │  More tests    │─── No ──►  DONE │
         │            │  in the list?  │                 │
         │            └────────┬───────┘                 │
         │                     │                         │
         │                    Yes                        │
         │                     │                         │
         └─────────────────────┘                         │
                                                         │
                 LOOP BACK TO TEST LIST AGENT            │
                 (Mark test done, update list,           │
                  get NEXT test from list)               │
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
    ║      │         ┌──────────────────────┐             │             ║
    ║      └─────────│  More tests in list? │◄────────────┘             ║
    ║                │  Mark current done   │                           ║
    ║                │  Get NEXT test       │                           ║
    ║                └──────────────────────┘                           ║
    ║                                                                   ║
    ╚═══════════════════════════════════════════════════════════════════╝
```

## Test List Format

The Test List Agent writes and maintains a test list file. Each test in the list has a status:

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
│  4. 🔵 REFACTOR COMMIT │  Review Agent commits refactored code      │
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

## Related Specifications

- [Handoffs Specification](spec-handoffs.md) - Agent context, information access, and handoff mechanisms
- [Error Handling Specification](spec-error-handling.md) - Failure scenarios and recovery strategies
