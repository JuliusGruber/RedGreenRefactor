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
- Cycle complete ✓

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
