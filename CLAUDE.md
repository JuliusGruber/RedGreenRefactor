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
