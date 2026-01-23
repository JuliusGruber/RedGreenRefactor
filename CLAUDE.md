# Claude Code Guidelines

## Context7 MCP Usage

Always use Context7 MCP when I need library/API documentation, code generation, setup or configuration steps without me having to explicitly ask.

## GitHub MCP Server

This project is configured with GitHub's official MCP server for PR and repository operations. Use the GitHub MCP tools for:

- **Merging PRs**: Use `merge_pull_request` with merge/squash/rebase options
- **PR management**: Create, update, review, and list pull requests
- **Repository operations**: Create branches, files, commits
- **Issue tracking**: Create and manage issues

**Setup requirement**: Set `GITHUB_PAT` environment variable with a GitHub Personal Access Token (needs `repo` scope for PR operations).

## Adding MCP Servers

When asked to add a new MCP server, add it to the `.mcp.json` file in the project root. This file contains the consolidated MCP server configuration for the project.

## Commit Requirements

Before making any commit:

1. **Verify the build passes** - Run the build command and ensure it completes successfully
2. **Run all tests** - Execute the full test suite and ensure all tests pass

Do not commit code that fails to build or has failing tests.
