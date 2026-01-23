/**
 * Git integration for TDD handoff coordination.
 *
 * <p>This package provides two main classes:</p>
 * <ul>
 *   <li>{@link com.redgreenrefactor.git.GitNotesManager} - Manages Git Notes for storing
 *       handoff state between agents using the {@code refs/notes/tdd-handoffs} namespace</li>
 *   <li>{@link com.redgreenrefactor.git.GitOperations} - Provides Git operations like
 *       committing changes, resetting, and generating diffs</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * try (GitNotesManager notesManager = new GitNotesManager(Path.of("."))) {
 *     // Write a handoff state
 *     HandoffState state = HandoffState.initial();
 *     notesManager.writeHandoff(commitId, state);
 *
 *     // Read it back
 *     Optional<HandoffState> retrieved = notesManager.readHandoff(commitId);
 *
 *     // Find the latest handoff
 *     Optional<HandoffEntry> latest = notesManager.findLatestHandoff();
 * }
 * }</pre>
 */
package com.redgreenrefactor.git;
