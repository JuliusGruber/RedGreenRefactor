# Error Handling Specification

> **Status: Designed**
>
> Error handling strategies for the TDD workflow, with recovery mechanisms coordinated through Git Notes.

## Overview

The orchestrator is responsible for detecting failures and coordinating recovery. Error states are recorded in Git Notes for audit trail and recovery context.

## Resolved Questions

### Test Agent Failures

| Failure | Detection | Recovery |
|---------|-----------|----------|
| Invalid/non-compiling test | Bash tool returns non-zero exit code | Re-run Test Agent with error context in prompt |
| Test passes immediately (should fail) | Test output shows "PASS" or exit code 0 during RED phase | Re-run Test Agent with instruction to verify assertions |

### Implementing Agent Failures

| Failure | Detection | Recovery |
|---------|-----------|----------|
| Cannot make test pass | Test still fails after implementation | Re-run with alternative approach hint, or escalate |
| Breaks existing tests | Other tests fail that were passing | Git rollback, try alternative implementation |

### Refactor Agent Failures

| Failure | Detection | Recovery |
|---------|-----------|----------|
| Refactoring breaks tests | Tests fail after refactoring | Git rollback to pre-refactor commit, re-run Refactor Agent |
| Retry attempts | Track in Git Notes | Maximum 3 retries before escalating to human review |

### General Recovery

| Question | Resolution |
|----------|------------|
| Who detects failures? | **Orchestrator** parses tool output and test results |
| Rollback strategy | Git revert to last known-good commit (identified via Git Notes) |
| Restart vs abort | Retry current phase up to 3 times, then abort cycle with error state in notes |

### Edge Cases

| Case | Resolution |
|------|------------|
| Duplicate test | Test Agent should check existing tests; orchestrator validates via grep before proceeding |
| Already covered test | Test List Agent marks as already covered; skip to next pending test |

## Failure Detection

The orchestrator detects failures by parsing tool output:

```java
public boolean isTestFailure(String bashOutput) {
    return bashOutput.contains("FAILED") ||
           bashOutput.contains("FAIL:") ||
           bashOutput.contains("Error") ||
           bashOutput.matches(".*Exit code: [^0].*");
}

public boolean isUnexpectedPass(String bashOutput, String phase) {
    if ("RED".equals(phase)) {
        return bashOutput.contains("OK") ||
               bashOutput.contains("passed") ||
               bashOutput.matches(".*Exit code: 0.*");
    }
    return false;
}
```

## Retry Strategy

Retries use exponential backoff:

| Attempt | Delay |
|---------|-------|
| 1 | 1 second |
| 2 | 2 seconds |
| 3 | 4 seconds |
| After 3 | Abort and record error |

## Error State in Git Notes

When an error occurs, the orchestrator records it in the handoff note:

```json
{
  "phase": "GREEN",
  "error": "Test still failing after implementation",
  "error_details": {
    "type": "TestFailure",
    "message": "Expected 200, got 404"
  },
  "retry_count": 2
}
```

## Recovery Strategies Summary

| Failure Type | Recovery Approach |
|--------------|-------------------|
| Test doesn't compile | Re-run Test Agent with error context in prompt |
| Test passes immediately | Re-run Test Agent with instruction to check assertions |
| Implementation breaks other tests | Rollback via git, try alternative approach |
| Refactoring breaks tests | Rollback via git, re-run Refactor Agent |
| API rate limit | Exponential backoff with retry |
| Network timeout | Retry with increased timeout |

## Open Questions

### For Future Implementation

- How should the orchestrator handle flaky tests?
- Should there be a human approval gate after N consecutive failures?
- How to handle partial failures (some tests pass, some fail unexpectedly)?
