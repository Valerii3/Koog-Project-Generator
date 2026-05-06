---
name: koog-agent-scaffolding
description: Builds and scaffolds AI agents with the Koog framework for Kotlin/JVM projects. Use when the user asks to create, configure, or debug a Koog agent, use koog-gen-cli, choose agent templates, configure LLM providers, add tools, enable MCP, persistence, observability, or implement graph/planner/functional agent workflows.
---

# koog-agent-scaffolding

## Workflow

### 1. Discover what you can pass in

Run the generator CLI against the live catalog:

```bash
npx koog-gen-cli@latest --help
```

Read the output end-to-end. It prints **usage**, then fetches `/api/options` and lists:

- **Agent template ids** (e.g. `BASIC`, `FUNCTIONAL`, `GRAPH`, `PLANNER_SIMPLE`, `PLANNER_CRITIC`)
- **Provider ids** (e.g. `OPENAI`, `OLLAMA`) and env vars where applicable
- **Tool ids** for `--tools` (comma-separated in non-interactive mode)
- **Feature ids** for `--features` (comma-separated; only meaningful for Basic agents)

Treat this as the source of truth for flag values. If the command fails or returns empty content, retry once.

### 2. Select relevant components

**Important (generator rules):**

- **Tools** are available only for **Basic**, **Functional**, and **Graph** agents. Planner agents ignore tools; do not pass `--tools` for planners.
- **Features** are available only for the **Basic** agent. For Functional / Graph / Planner, do not pass `--features`.

**Recommendation for agents:**

| Template | When to use |
|----------|-------------|
| **BASIC** | Default. Standard `AIAgent` with optional tools and features (memory, tracing, persistence, …). |
| **FUNCTIONAL** | Custom flow via `functionalStrategy`; tools allowed; no feature flags from the generator. |
| **GRAPH** | Explicit nodes/edges and tool loops; tools allowed; no feature flags from the generator. |
| **PLANNER_SIMPLE** | One-shot plan then execute; no tools/features in this generator. |
| **PLANNER_CRITIC** | Planner with critic step; no tools/features in this generator. |

Prefer **BASIC** unless the user clearly needs custom strategy logic, a graph, or a planner/critic.


### 3. Fetch and apply

Scaffold with **non-interactive** flags, passing **only** arguments the app needs (omit `--tools` / `--features` when unused). Output is a single **`Agent.kt`** (generator `Main.kt` content)

```bash
npx koog-gen-cli@latest \
  --artifact <gradle.id.like.com.example.app> \
  --agent <AGENT_ID> \
  --provider <PROVIDER_ID> \
  [--tools TOOL1,TOOL2] \
  [--features FEAT1,FEAT2] \
  [--output Agent.kt] \
  --force
```

- **`-o` / `--output`**: defaults to `./Agent.kt` in the current working directory.
- **`--force`**: required to overwrite an existing file in non-interactive mode.