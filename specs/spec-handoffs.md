# Handoffs Specification

## Open Questions

### Agent Context and Information Access

- Does each agent see the full codebase, or only specific files?
- Does each agent know about previous cycles, or is it stateless?
- Does the Test Agent only receive the test description, or also the current implementation state?
- What is the mechanism for handoff (file, message, git commit, etc.)?

### Handoff Data Structure

- What exact information is passed from Test List Agent to Test Agent?
- What exact information is passed from Test Agent to Implementing Agent?
- What exact information is passed from Implementing Agent to Review Agent?
- What exact information is passed from Review Agent back to Test List Agent?
