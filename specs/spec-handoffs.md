# Handoffs Specification

> **Status: Needs Design**
>
> The handoff mechanism is a critical component that coordinates transitions between independent Claude Code sessions. This specification needs to be fully designed.

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
- What exact information is passed from Implementing Agent to Review Agent?
- What exact information is passed from Review Agent back to Test List Agent?

### Git as Shared State

- Is git the only shared state mechanism?
- Should there be a handoff file (e.g., `.handoff.json`) in the repository?
- How does a session know what the previous session accomplished?
