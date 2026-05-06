#!/usr/bin/env node
import { writeFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { select, input, checkbox, confirm } from '@inquirer/prompts'

/** Default API origin (Railway production). Override with --url or KOOG_GENERATOR_URL for local/staging. */
const DEFAULT_GENERATOR_URL = 'https://koog-project-generator-production.up.railway.app'

/** CLI always requests a single Kotlin file (`Main.kt` content saved as `Agent.kt`), never a ZIP. */
const OUTPUT_FORMAT = 'agent_kotlin'

const DEFAULT_AGENT_FILE = 'Agent.kt'

function parseArgs(argv) {
  let url = process.env.KOOG_GENERATOR_URL ?? DEFAULT_GENERATOR_URL
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--url' && argv[i + 1]) {
      url = argv[++i]
    }
  }
  return { url: url.replace(/\/$/, '') }
}

async function fetchOptions(baseUrl) {
  const res = await fetch(`${baseUrl}/api/options`)
  if (!res.ok) {
    throw new Error(`GET /api/options failed: ${res.status} ${await res.text()}`)
  }
  return res.json()
}

function isPlanner(agentType) {
  return agentType === 'PLANNER_SIMPLE' || agentType === 'PLANNER_CRITIC'
}

function normalizeAfterAgentChange(state) {
  if (isPlanner(state.agentType)) {
    state.tools = []
    state.features = []
  } else if (state.agentType !== 'BASIC') {
    state.features = []
  }
}

async function promptArtifact(state) {
  state.artifact = await input({
    message: 'Project artifact (e.g. com.example.hello)',
    default: state.artifact || 'com.example.hello',
  })
}

async function promptAgentType(options, state) {
  state.agentType = await select({
    message: 'Agent template',
    choices: options.agentTypes.map((a) => ({
      name: `${a.label} — ${a.description}`,
      value: a.id,
    })),
    default: state.agentType,
  })
  normalizeAfterAgentChange(state)
}

async function promptProvider(options, state) {
  state.provider = await select({
    message: 'LLM provider',
    choices: options.providers.map((p) => ({
      name: p.envVar ? `${p.label} (${p.envVar})` : p.label,
      value: p.id,
      description: p.description,
    })),
    default: state.provider,
  })
}

async function promptTools(options, state) {
  if (isPlanner(state.agentType)) {
    state.tools = []
    return
  }
  state.tools = await checkbox({
    message:
      'Tools — pick any combination (↑↓ move, space = toggle on/off, enter = done)',
    choices: options.tools.map((t) => ({
      name: `${t.label} — ${t.description}`,
      value: t.id,
      checked: state.tools.includes(t.id),
    })),
  })
}

async function promptFeatures(options, state) {
  if (state.agentType !== 'BASIC') {
    state.features = []
    return
  }
  state.features = await checkbox({
    message:
      'Features (Basic only) — ↑↓ move, space = toggle multiple items, enter = done',
    choices: options.features
      .filter((f) => f.implemented)
      .map((f) => ({
        name: `${f.label} — ${f.description}`,
        value: f.id,
        checked: state.features.includes(f.id),
      })),
  })
}

async function promptOutputPath(state) {
  const outRaw = await input({
    message: 'Save generated Kotlin as (path)',
    default: state.outPath || resolve(process.cwd(), DEFAULT_AGENT_FILE),
  })
  state.outPath = resolve(outRaw)
}

function summaryLine(state) {
  const tools = isPlanner(state.agentType)
    ? '(skipped for planner)'
    : state.tools.length
      ? state.tools.join(', ')
      : 'none'
  const feats =
    state.agentType !== 'BASIC'
      ? '(only for Basic)'
      : state.features.length
        ? state.features.join(', ')
        : 'none'
  return [
    `artifact: ${state.artifact}`,
    `agent: ${state.agentType}`,
    `provider: ${state.provider}`,
    `tools: ${tools}`,
    `features: ${feats}`,
    `out: ${state.outPath}`,
  ].join('\n  ')
}

async function main() {
  const { url } = parseArgs(process.argv.slice(2))

  const options = await fetchOptions(url)

  const state = {
    artifact: 'com.example.hello',
    agentType: options.agentTypes[0]?.id ?? 'BASIC',
    provider: options.providers[0]?.id ?? 'OPENAI',
    tools: [],
    features: [],
    outPath: resolve(process.cwd(), DEFAULT_AGENT_FILE),
  }

  await promptArtifact(state)
  await promptAgentType(options, state)
  await promptProvider(options, state)
  await promptTools(options, state)
  await promptFeatures(options, state)
  await promptOutputPath(state)

  while (true) {
    console.error(`\nCurrent configuration:\n  ${summaryLine(state)}\n`)
    const action = await select({
      message: 'What next?',
      choices: [
        { name: 'Generate Agent.kt', value: 'generate' },
        { name: 'Edit artifact', value: 'artifact' },
        { name: 'Edit agent template', value: 'agent' },
        { name: 'Edit LLM provider', value: 'provider' },
        {
          name: isPlanner(state.agentType)
            ? 'Tools (N/A for planner)'
            : 'Edit tools',
          value: 'tools',
          disabled: isPlanner(state.agentType),
        },
        {
          name:
            state.agentType !== 'BASIC'
              ? 'Features (Basic agent only)'
              : 'Edit features',
          value: 'features',
          disabled: state.agentType !== 'BASIC',
        },
        { name: 'Edit output path', value: 'output' },
      ],
    })

    switch (action) {
      case 'generate':
        break
      case 'artifact':
        await promptArtifact(state)
        continue
      case 'agent':
        await promptAgentType(options, state)
        continue
      case 'provider':
        await promptProvider(options, state)
        continue
      case 'tools':
        await promptTools(options, state)
        continue
      case 'features':
        await promptFeatures(options, state)
        continue
      case 'output':
        await promptOutputPath(state)
        continue
      default:
        continue
    }
    break
  }

  if (existsSync(state.outPath)) {
    const ok = await confirm({
      message: `File exists: ${state.outPath}. Overwrite?`,
      default: false,
    })
    if (!ok) {
      console.error('Cancelled.')
      process.exit(0)
    }
  }

  const res = await fetch(`${url}/api/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      artifact: state.artifact.trim(),
      agentType: state.agentType,
      provider: state.provider,
      tools: state.tools,
      features: state.features,
      outputFormat: OUTPUT_FORMAT,
    }),
  })

  if (!res.ok) {
    console.error(await res.text())
    process.exit(1)
  }

  const text = await res.text()
  writeFileSync(state.outPath, text, 'utf8')
  console.error(`Wrote ${state.outPath} (${text.length} chars)`)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
