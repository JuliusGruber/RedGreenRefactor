/**
 * Agent definitions and invocation for the TDD orchestrator.
 *
 * <p>This package contains the four TDD agents:
 * <ul>
 *   <li>{@link com.redgreenrefactor.agent.TestListAgent} - Planning agent for managing the test list</li>
 *   <li>{@link com.redgreenrefactor.agent.TestAgent} - Red phase agent for writing failing tests</li>
 *   <li>{@link com.redgreenrefactor.agent.ImplementingAgent} - Green phase agent for making tests pass</li>
 *   <li>{@link com.redgreenrefactor.agent.RefactorAgent} - Refactor phase agent for improving code</li>
 * </ul>
 *
 * <p>The {@link com.redgreenrefactor.agent.AgentInvoker} handles invoking agents via the
 * Anthropic API and managing the tool use conversation loop.
 */
package com.redgreenrefactor.agent;
