# Error Handling Specification

> **Status: Resolved**
>
> This document defines the error handling and recovery strategies for the RedGreenRefactor TDD workflow.

## General Configuration

| Setting | Value |
|---------|-------|
| **Default retry limit** | 5 retries (6 total attempts) |
| **Rollback target** | Commit before current phase started |
| **Workflow resume** | Auto-resume from last Git Note state |

## Test Agent Failures (Red Phase)

### Invalid or Non-Compiling Test

**Scenario**: The Test Agent writes a test with syntax errors, missing imports, or other compilation issues.

**Strategy**: Retry with error context
- Retry the Test Agent with the compiler error message included in the prompt
- Maximum 5 retries (6 total attempts)
- If all retries fail: Abort workflow and report to user

### Test Passes Immediately

**Scenario**: The new test passes immediately when it should fail (indicating it's not testing new behavior).

**Strategy**: Skip and mark problematic
- Skip this test and move to the next one in the test list
- Mark the test as "problematic" in the test list
- Log a warning for user review
- Continue with the next pending test

### Duplicate Test

**Scenario**: The Test Agent writes a test that already exists or is very similar to an existing test.

**Strategy**: Skip to next test
- Skip this test and move to the next one in the test list
- Do not create a duplicate test file
- Continue with the next pending test

## Implementing Agent Failures (Green Phase)

### Cannot Make Test Pass

**Scenario**: The Implementing Agent exhausts all attempts but cannot make the test pass.

**Strategy**: Abort workflow
- Abort the entire workflow immediately
- Report the failure to the user for manual intervention
- Preserve all commits and Git Notes for debugging
- User must manually resolve before resuming

### Implementation Breaks Existing Tests

**Scenario**: The new implementation makes the target test pass but breaks one or more previously passing tests.

**Strategy**: Retry with broken test info
- Retry the Implementing Agent with information about which tests broke
- Include the failing test names and error messages in the retry prompt
- Maximum 5 retries (6 total attempts)
- If all retries fail: Abort workflow and report to user

## Refactor Agent Failures (Refactor Phase)

### Refactoring Breaks Tests

**Scenario**: The Refactor Agent's changes cause one or more tests to fail.

**Strategy**: Retry with failure info
- Retry the Refactor Agent with the test failure information
- Include which tests failed and the error messages
- Maximum 5 retries (6 total attempts)
- If all retries fail: Rollback refactoring changes and commit "refactor: no changes (tests would break)"

## Network and API Failures

### API Rate Limits

**Strategy**: Exponential backoff with retry
- Wait times: 1s, 2s, 4s, 8s, 16s, 32s...
- Continue retrying until rate limit window passes
- Log each retry attempt

### Network Timeouts

**Strategy**: Retry with same timeout
- Retry up to 3 times with the same timeout setting
- If all retries fail: Abort and report to user

## Recovery and Resume

### Workflow Interruption

**Scenario**: The orchestrator crashes or is interrupted mid-workflow.

**Strategy**: Auto-resume from Git Notes
- On restart, automatically detect last known state from Git Notes
- Resume from the next phase after the last successful commit
- No manual `--resume` flag required

### Rollback Mechanism

When rollback is needed:
1. Identify the commit before the current phase started
2. Use `git reset --hard <commit>` to restore state
3. Update Git Notes to reflect the rollback
4. Either retry the phase or abort based on failure type

## Error State in Git Notes

When an error occurs, the handoff state includes error details:

```json
{
  "phase": "GREEN",
  "next_phase": "GREEN",
  "cycle": 1,
  "current_test": {
    "description": "User can log in with valid credentials",
    "test_file": "tests/test_user_login.py"
  },
  "test_result": "FAIL",
  "error": {
    "type": "IMPLEMENTATION_FAILED",
    "message": "Could not make test pass after 6 attempts",
    "details": "AssertionError: expected True but got False",
    "retry_count": 6,
    "timestamp": "2025-01-22T10:30:00Z"
  }
}
```

## Error Types

| Error Type | Description |
|------------|-------------|
| `COMPILATION_ERROR` | Test or implementation code doesn't compile |
| `TEST_UNEXPECTED_PASS` | Test passed when it should have failed (Red phase) |
| `TEST_UNEXPECTED_FAIL` | Test failed when it should have passed (Green/Refactor) |
| `IMPLEMENTATION_FAILED` | Could not make test pass after all retries |
| `REGRESSION` | Previously passing tests now fail |
| `DUPLICATE_TEST` | Test already exists in codebase |
| `API_RATE_LIMIT` | Anthropic API rate limit hit |
| `NETWORK_TIMEOUT` | Network request timed out |
| `WORKFLOW_ABORTED` | Workflow aborted due to unrecoverable error |

## Summary Table

| Failure Scenario | Strategy | Max Retries | On Exhaustion |
|------------------|----------|-------------|---------------|
| Non-compiling test | Retry with error | 5 | Abort |
| Test passes immediately | Skip test | — | Continue |
| Duplicate test | Skip test | — | Continue |
| Can't make test pass | Abort | — | Manual intervention |
| Implementation breaks tests | Retry with info | 5 | Abort |
| Refactoring breaks tests | Retry with info | 5 | Rollback & continue |
| API rate limit | Exponential backoff | Unlimited | — |
| Network timeout | Retry same timeout | 3 | Abort |
