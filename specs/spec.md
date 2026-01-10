# RedGreenRefactor Agent Workflow Specification

## Main Idea

Four sessions of the same coding agent collaborate in a Test-Driven Development pipeline. Each agent has a specialized role and hands off to the next agent in sequence, following the Red-Green-Refactor cycle.

## Agent Roles

### 1. Test List Agent (Planning)
- Receives a feature request
- Analyzes requirements and breaks them down
- Creates a comprehensive test list
- **Handoff →** Test Agent

### 2. Test Agent (Red Phase)
- Receives the test list
- Writes failing tests based on the test list
- Ensures tests are focused and well-structured
- **Commits** the failing tests
- **Handoff →** Implementing Agent

### 3. Implementing Agent (Green Phase)
- Receives failing tests
- Writes minimum code to make tests pass
- Focuses on functionality, not perfection
- **Commits** the passing implementation
- **Handoff →** Review Agent

### 4. Review Agent (Refactor Phase)
- Reviews the implementation
- Refactors code while keeping tests green
- Improves code quality, readability, and maintainability
- **Commits** the refactored code
- **Handoff →** Test List Agent (for next test selection)

## The TDD Process Philosophy

Although the three steps—often summarized as **Red - Green - Refactor**—are the heart of the process, there's also a vital initial step where we write out a list of test cases first. We then pick one of these tests, apply red-green-refactor to it, and once we're done pick the next.

**Key principles:**
- **Sequencing tests properly is a skill**: We want to pick tests that drive us quickly to the salient points in the design
- **The test list is dynamic**: During the process, we should add more tests to our list as they occur to us
- **Iterative refinement**: Each completed cycle informs the next test selection

This means the **Test List Agent** runs not only at the start, but also **after each red-green-refactor cycle** to:
1. Review the current test list
2. Evaluate which tests have been completed
3. Add any new tests discovered during implementation
4. Select the next most valuable test to implement
5. Hand off to the Test Agent for the next cycle

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
┌─────────────────────┐
│    TEST AGENT       │  ← 🔴 RED
│ (Writes failing     │
│      tests)         │
└──────────┬──────────┘
           │
           ▼
     [COMMIT 1: Red]
           │
           ▼
┌─────────────────────┐
│ IMPLEMENTING AGENT  │  ← 🟢 GREEN
│ (Makes tests pass)  │
└──────────┬──────────┘
           │
           ▼
    [COMMIT 2: Green]
           │
           ▼
┌─────────────────────┐
│   REVIEW AGENT      │  ← 🔵 REFACTOR
│ (Improves code)     │
└──────────┬──────────┘
           │
           ▼
  [COMMIT 3: Refactor]
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
    │                   │            │              │               │
    │                   ▼            ▼              ▼               │
    │               COMMIT 1     COMMIT 2      COMMIT 3           │
    │                                                              │
    └──────────────────────────────────────────────────────────────┘
```

## Iterative TDD Cycle Diagram

The following diagram shows how the Test List Agent is revisited after each Red-Green-Refactor cycle to select the next test:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ITERATIVE TDD CYCLE WITH TEST PLANNING                   │
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
              │  │ □ Test case 1                 │  │
              │  │ □ Test case 2                 │  │
              │  │ □ Test case 3                 │  │
              │  │ □ ...more tests as discovered │  │
              │  └───────────────────────────────┘  │
              │                                     │
              │  ► Select next most valuable test   │
              │  ► Add new tests as they occur      │
              └──────────────────┬──────────────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         │                       ▼                       │
         │         ┌─────────────────────────┐           │
         │         │      TEST AGENT         │           │
         │         │    🔴 RED PHASE         │           │
         │         │  (Write failing test)   │           │
         │         └───────────┬─────────────┘           │
         │                     │                         │
         │                     ▼                         │
         │              [COMMIT: Red]                    │
         │                     │                         │
         │                     ▼                         │
         │         ┌─────────────────────────┐           │
         │         │  IMPLEMENTING AGENT     │           │
         │         │    🟢 GREEN PHASE       │           │
         │         │  (Make test pass)       │           │
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
         │            │  remaining?    │                 │
         │            └────────┬───────┘                 │
         │                     │                         │
         │                    Yes                        │
         │                     │                         │
         └─────────────────────┘                         │
                                                         │
                 LOOP BACK TO TEST LIST AGENT            │
                 (Review list, add new tests,            │
                  select next test)                      │
                                                         │
─────────────────────────────────────────────────────────┘
```

### The Iterative Process

```
    ╔═══════════════════════════════════════════════════════════════════╗
    ║                                                                   ║
    ║   📋 PLAN ──► 🔴 RED ──► 🟢 GREEN ──► 🔵 REFACTOR ──┐            ║
    ║      ▲                                              │             ║
    ║      │                                              │             ║
    ║      │         ┌──────────────────────┐             │             ║
    ║      └─────────│  More tests to do?   │◄────────────┘             ║
    ║                │  Add discovered tests│                           ║
    ║                │  Pick next test      │                           ║
    ║                └──────────────────────┘                           ║
    ║                                                                   ║
    ╚═══════════════════════════════════════════════════════════════════╝
```

## Commit Structure

Each TDD cycle produces **three commits**, one from each active agent:

```
┌─────────────────────────────────────────────────────────────────────┐
│                     COMMITS PER TDD CYCLE                           │
├─────────────────────────────────────────────────────────────────────┤
│  1. 🔴 RED COMMIT      │  Test Agent commits failing tests          │
│  2. 🟢 GREEN COMMIT    │  Implementing Agent commits passing code   │
│  3. 🔵 REFACTOR COMMIT │  Review Agent commits refactored code      │
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
