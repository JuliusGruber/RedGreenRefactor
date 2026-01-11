# Handoffs Specification

> **Status: Needs Design**
>
> The handoff mechanism is a critical component that coordinates transitions between independent Claude Code sessions. This specification needs to be fully designed.

## Design Decisions

| Decision | Choice |
|----------|--------|
| Session persistence | **New session each time** - every agent invocation starts a fresh Claude Code session, even when the same role runs again (e.g., Test List Agent in each cycle) |

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
- What exact information is passed from Implementing Agent to Refactor Agent?
- What exact information is passed from Refactor Agent back to Test List Agent?

### Git as Shared State

- Is git the only shared state mechanism?
- Should there be a handoff file (e.g., `.handoff.json`) in the repository?
- How does a session know what the previous session accomplished?

### Pre-existing Codebase

- How does the workflow handle existing codebases with existing tests?
- Should the Test List Agent account for existing tests when planning?
- Do existing tests count toward "all tests must pass"?
- Is this workflow scoped to new features only, or can it modify existing functionality?

---

## Brainstorm: Inter-Agent Communication Mechanisms

> This section explores every possible approach for agent handoffs and communication. No evaluation yet—just possibilities.

### 1. Git-Native Approaches

#### 1.1 Conventional Commits with Metadata
- Encode handoff data in commit messages (structured format)
- Next agent parses the last commit to understand state
- Example: `[TEST-AGENT] test: add user validation test | next:IMPL | test_file:tests/user.test.ts`

#### 1.2 Git Notes
- Attach metadata to commits without modifying commit messages
- `git notes add -m '{"next_agent": "impl", "test_file": "..."}'`
- Non-intrusive, doesn't pollute history

#### 1.3 Git Tags
- Lightweight tags marking handoff points
- `git tag handoff/test-agent/cycle-1`
- Easy to query: `git tag --list 'handoff/*'`

#### 1.4 Git Refs
- Custom refs in `.git/refs/handoff/`
- Point to specific commits representing state
- Example: `refs/handoff/current-agent`, `refs/handoff/last-test`

#### 1.5 Git Branches as State
- Each agent works on a branch, merges when complete
- Branch names encode state: `tdd/cycle-3/green-phase`
- Parallel work possible with proper merge strategy

#### 1.6 Git Hooks
- Post-commit hooks trigger next agent
- Pre-commit hooks validate handoff readiness
- Can invoke external orchestrator

#### 1.7 Git Worktrees
- Separate worktrees for each agent
- Isolated working directories, shared repository
- Reduces conflicts, enables parallelism

### 2. File-Based Approaches

#### 2.1 Handoff State File
- `.handoff.json` or `.tdd-state.yaml` in repo root
- Contains: current phase, next agent, context data
- Simple, human-readable, version-controlled

#### 2.2 Message Queue File
- Append-only log file (`.tdd-queue.log`)
- Each agent appends its completion message
- Next agent reads last entry

#### 2.3 Lock Files
- `.tdd.lock` indicates active agent
- Contains agent ID, timestamp, phase
- Prevents concurrent execution

#### 2.4 Dedicated Handoff Directory
- `.handoff/` directory with structured files
- `current.json`, `history/`, `context/`
- More organized for complex state

#### 2.5 Named Pipes (FIFO)
- Unix named pipes for real-time IPC
- Agent writes completion, orchestrator reads
- Low latency, OS-level coordination

#### 2.6 Unix Domain Sockets
- Local socket for bidirectional communication
- More flexible than pipes
- Supports request/response pattern

#### 2.7 Shared Memory / Memory-Mapped Files
- Fastest IPC mechanism
- Shared memory region between processes
- Requires careful synchronization

### 3. External Orchestration

#### 3.1 Claude Agent SDK
- Use Claude's native SDK for multi-agent orchestration
- Agents as tool calls within larger conversation
- SDK manages context and handoffs

#### 3.2 Shell Script Orchestrator
- Bash script invoking agents sequentially
- Passes arguments/environment variables
- Simple, no dependencies

#### 3.3 Python Orchestrator
- Python script managing agent lifecycle
- Can use subprocess, threading, async
- Rich ecosystem for state management

#### 3.4 Node.js Orchestrator
- JavaScript/TypeScript orchestrator
- Event-driven, async-native
- Good for real-time coordination

#### 3.5 Make / Task Runner
- Makefile defining agent targets
- Dependencies express workflow order
- `make test-agent` triggers after `make plan-agent`

#### 3.6 Just (justfile)
- Modern command runner
- Cleaner syntax than Make
- Variables for state passing

#### 3.7 GitHub Actions / CI Pipelines
- Workflow as code (YAML)
- Jobs represent agents
- Artifacts pass state between jobs

#### 3.8 Temporal / Cadence Workflows
- Durable execution platform
- Workflow as code with automatic retries
- Built-in state management

### 4. Message Queue Systems

#### 4.1 Redis Pub/Sub
- In-memory message broker
- Channels per agent or phase
- Fast, simple setup

#### 4.2 Redis Streams
- Persistent message log
- Consumer groups for agent coordination
- Better durability than pub/sub

#### 4.3 RabbitMQ
- Full-featured message broker
- Queues per agent role
- Acknowledgments, routing, persistence

#### 4.4 Apache Kafka
- Distributed streaming platform
- Topics for different phases
- Full audit trail, replay capability

#### 4.5 NATS
- Lightweight, high-performance messaging
- Simple pub/sub and request/reply
- Cloud-native, minimal footprint

#### 4.6 ZeroMQ
- Brokerless messaging library
- Direct agent-to-agent communication
- Multiple patterns (req/rep, pub/sub, push/pull)

#### 4.7 Amazon SQS / Google Pub/Sub / Azure Service Bus
- Cloud-managed message queues
- Automatic scaling, high availability
- Pay-per-use

### 5. Database-Based State

#### 5.1 SQLite as Shared State
- Single file database in repo
- Table for workflow state, history
- ACID transactions for consistency

#### 5.2 PostgreSQL / MySQL
- Networked relational database
- Row-level locking for coordination
- Triggers for notifications

#### 5.3 Redis as State Store
- Key-value with expiration
- Atomic operations (INCR, SETNX)
- Pub/sub for notifications

#### 5.4 etcd
- Distributed key-value store
- Watch mechanism for changes
- Strong consistency guarantees

#### 5.5 Consul
- Service discovery + KV store
- Health checks, sessions
- Distributed locking

#### 5.6 DynamoDB / Firestore
- Cloud-native document stores
- Change streams for notifications
- Serverless, auto-scaling

### 6. API-Based Coordination

#### 6.1 REST API Orchestrator
- HTTP server managing state
- Agents poll or webhook on completion
- Stateless agents, stateful server

#### 6.2 GraphQL API
- Query/mutation-based state management
- Subscriptions for real-time updates
- Typed schema for handoff data

#### 6.3 gRPC
- High-performance RPC framework
- Bidirectional streaming
- Strong typing with protobuf

#### 6.4 WebSockets
- Persistent bidirectional connection
- Real-time coordination
- Server pushes next task to agent

#### 6.5 Server-Sent Events (SSE)
- Server pushes events to agents
- Simpler than WebSockets for one-way
- Automatic reconnection

### 7. Claude-Specific Mechanisms

#### 7.1 MCP (Model Context Protocol) Servers
- Claude's native extension mechanism
- Custom MCP server for state management
- Tools for reading/writing handoff state

#### 7.2 Claude Code Hooks
- Pre/post command hooks
- Hook on tool calls or completions
- Inject state into agent context

#### 7.3 System Prompt Injection
- Orchestrator constructs system prompt with state
- Each session receives full context
- No runtime communication needed

#### 7.4 CLAUDE.md as State
- Use CLAUDE.md file for handoff context
- Automatically read by Claude Code
- Can include current phase, instructions

#### 7.5 Multi-Turn Conversation Continuity
- Parent conversation spawns child sessions
- Pass conversation history or summary
- Pseudo-memory through context

#### 7.6 Tool Results as Handoffs
- Agent returns structured data via tool
- Orchestrator parses and routes
- Clean contract between agents

### 8. Process Orchestration

#### 8.1 Docker Compose
- Each agent as a container
- Depends_on for ordering
- Shared volumes for state

#### 8.2 Kubernetes Jobs
- Jobs or CronJobs for agents
- Init containers for dependencies
- ConfigMaps/Secrets for state

#### 8.3 Systemd Services
- Linux service management
- Dependencies, ordering, restart policies
- Journal for logging

#### 8.4 Supervisord
- Process control system
- Event listeners for coordination
- Programmatic control via XML-RPC

#### 8.5 PM2
- Node.js process manager
- Cluster mode, watch, restart
- IPC between processes

### 9. Self-Coordination (Decentralized)

#### 9.1 Polling-Based
- Agents poll shared state (file, db, api)
- Check for their turn, execute, update state
- Simple but has latency

#### 9.2 File Watch / inotify
- Watch for file changes
- Trigger on handoff file modification
- Lower latency than polling

#### 9.3 Semaphore Files
- Create file to signal completion
- Next agent waits for file existence
- `test-agent-done.signal`

#### 9.4 Atomic File Rename
- Write to temp file, atomic rename to signal
- Guarantees complete write before read
- Cross-platform safe

#### 9.5 Leader Election
- Agents compete for lock
- Winner executes, releases lock
- ZooKeeper, etcd, or file-based

### 10. Human-in-the-Loop

#### 10.1 Manual Triggering
- Human runs each agent explicitly
- Full control, no automation
- Good for debugging/learning

#### 10.2 Approval Gates
- Agent completes, waits for human approval
- Review code before next phase
- CI/CD style gates

#### 10.3 CLI Interactive Mode
- Orchestrator prompts for next action
- Human can intervene, retry, skip
- Hybrid automation

#### 10.4 Slack/Discord Bot
- Notifications on completion
- Commands to trigger next agent
- Team visibility

### 11. Event-Driven Architectures

#### 11.1 EventEmitter Pattern
- In-process event bus
- Agents subscribe to events
- Orchestrator emits transitions

#### 11.2 AWS EventBridge / CloudEvents
- Cloud event bus
- Rule-based routing
- Cross-service coordination

#### 11.3 Webhooks
- Agents expose HTTP endpoints
- Orchestrator calls on transitions
- Decoupled, scalable

#### 11.4 Actor Model (Akka, Orleans)
- Agents as actors with mailboxes
- Message passing, supervision
- Location transparency

### 12. Workflow Engines

#### 12.1 Apache Airflow
- DAG-based workflow orchestration
- Rich UI, scheduling, monitoring
- Python operators for agents

#### 12.2 Prefect / Dagster
- Modern data orchestration
- Python-native, type-safe
- Built-in retries, caching

#### 12.3 n8n / Node-RED
- Visual workflow builders
- Low-code agent coordination
- Extensive integrations

#### 12.4 Step Functions (AWS)
- State machine as a service
- JSON-based workflow definition
- Automatic retries, error handling

#### 12.5 Argo Workflows
- Kubernetes-native workflows
- Container-based steps
- DAG and step-based execution

### 13. Hybrid Approaches

#### 13.1 Git + Webhook
- Git commits trigger webhooks
- Webhook server routes to next agent
- Best of both worlds

#### 13.2 File + Process Manager
- State in files, process manager orchestrates
- Supervisord watches files, spawns agents
- Simple infrastructure

#### 13.3 MCP + Git
- MCP server reads/writes git state
- Agents use MCP tools for coordination
- Native Claude integration

#### 13.4 SQLite + File Watch
- SQLite for state, file change for notification
- WAL mode for concurrent access
- Atomic transactions with fast signaling

---

## Evaluation Criteria (For Future Selection)

When selecting an approach, consider:

| Criterion | Description |
|-----------|-------------|
| **Simplicity** | Easy to understand, implement, debug |
| **Dependencies** | External services required |
| **Portability** | Works across OS, environments |
| **Observability** | Easy to inspect state, debug issues |
| **Reliability** | Handles failures, crashes gracefully |
| **Latency** | Time between handoffs |
| **Scalability** | Can handle multiple concurrent workflows |
| **Version Control** | State is tracked in git history |
| **Claude Native** | Leverages Claude Code capabilities |
| **Human Readable** | State can be read/edited manually |
