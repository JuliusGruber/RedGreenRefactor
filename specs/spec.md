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
- **Handoff →** Implementing Agent

### 3. Implementing Agent (Green Phase)
- Receives failing tests
- Writes minimum code to make tests pass
- Focuses on functionality, not perfection
- **Handoff →** Review Agent

### 4. Review Agent (Refactor Phase)
- Reviews the implementation
- Refactors code while keeping tests green
- Improves code quality, readability, and maintainability
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
┌─────────────────────┐
│ IMPLEMENTING AGENT  │  ← 🟢 GREEN
│ (Makes tests pass)  │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   REVIEW AGENT      │  ← 🔵 REFACTOR
│ (Improves code)     │
└──────────┬──────────┘
           │
           ▼
    ┌──────────────┐
    │   COMPLETE   │
    └──────────────┘
```

## Cycle Flow

```
    ┌────────────────────────────────────────────────────┐
    │                                                    │
    │   📋 PLAN ──► 🔴 RED ──► 🟢 GREEN ──► 🔵 REFACTOR │
    │      │          │           │             │        │
    │   Test List   Failing    Passing      Improved    │
    │    Agent       Tests      Tests         Code      │
    │                                                    │
    └────────────────────────────────────────────────────┘
```

## Benefits

- **Separation of Concerns**: Each agent focuses on one task
- **Quality Assurance**: Tests written before implementation
- **Clean Code**: Dedicated refactoring phase ensures maintainability
- **Traceability**: Clear handoffs between phases
