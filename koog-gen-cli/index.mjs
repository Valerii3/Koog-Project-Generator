#!/usr/bin/env node
import { writeFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { parseArgs } from 'node:util'
import { select, input, checkbox, confirm } from '@inquirer/prompts'

/** Default API origin (Railway production). Override with --url or KOOG_GENERATOR_URL for local/staging. */
const DEFAULT_GENERATOR_URL = 'https://koog-project-generator-production.up.railway.app'

/** CLI always requests a single Kotlin file (`Main.kt` content saved as `Agent.kt`), never a ZIP. */
const OUTPUT_FORMAT = 'agent_kotlin'

const DEFAULT_AGENT_FILE = 'Agent.kt'

function baseUrlFromEnvAndArgs(values) {
  const u = values.url ?? process.env.KOOG_GENERATOR_URL ?? DEFAULT_GENERATOR_URL
  return String(u).replace(/\/$/, '')
}

function splitCsv(s) {
  if (s == null || s === '') return []
  return String(s)
    .split(',')
    .map((x) => x.trim())
    .filter(Boolean)
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

function printUsage() {
  console.log(`koog-gen — download Agent.kt from the Koog project generator

Usage:
  koog-gen                                    Interactive wizard
  koog-gen --artifact ID --agent TYPE --provider P [options]   Non-interactive

Options:
  -h, --help              Print this text and catalog from the server (agents, providers, tools, features)
  --url <origin>          API base URL (default: KOOG_GENERATOR_URL or Railway production)

  -a, --artifact <id>     Gradle-style id, e.g. com.example.hello
      --agent <type>      BASIC | FUNCTIONAL | GRAPH | PLANNER_SIMPLE | PLANNER_CRITIC
  -p, --provider <id>     e.g. OPENAI, ANTHROPIC, OLLAMA, …

      --tools <csv>       Comma-separated tool ids (see --help). Omit for none. Ignored for planner agents.
      --features <csv>    Comma-separated feature ids, Basic agent only. Omit for none.

  -o, --output <path>     Output .kt file (default: ./Agent.kt in the current working directory)
  -f, --force             Overwrite output if it exists (non-interactive; otherwise refuses)

Non-interactive example:
  koog-gen -a com.demo.app --agent BASIC -p OPENAI \\
    --tools BUILT_IN,ANNOTATION_BASED \\
    --features CHAT_MEMORY,TRACING \\
    -o Agent.kt --force
`)
}

function printCatalog(opt) {
  console.log('--- Agent templates ---')
  for (const a of opt.agentTypes) {
    console.log(`  ${a.id}`)
    console.log(`      ${a.label} — ${a.description}`)
  }

  console.log('\n--- LLM providers ---')
  for (const p of opt.providers) {
    const env = p.envVar ? ` (env: ${p.envVar})` : ''
    console.log(`  ${p.id}${env}`)
    console.log(`      ${p.label} — ${p.description}`)
  }

  console.log('\n--- Tools (comma-separated for --tools) ---')
  for (const t of opt.tools) {
    console.log(`  ${t.id}`)
    console.log(`      ${t.label} — ${t.description}`)
  }

  console.log('\n--- Features (comma-separated for --features; Basic agent only) ---')
  for (const f of opt.features) {
    const impl = f.implemented ? '' : ' [not implemented in generator]'
    console.log(`  ${f.id}${impl}`)
    console.log(`      ${f.label} — ${f.description}`)
  }
  console.log('')
}

async function printHelp(url) {
  printUsage()
  try {
    const opt = await fetchOptions(url)
    printCatalog(opt)
  } catch (e) {
    console.error('Could not fetch catalog from /api/options:', e.message)
    console.error(`Check --url (default: ${url})\n`)
    process.exitCode = 1
  }
}

function validateBatchChoices(apiOptions, state) {
  const agents = new Set(apiOptions.agentTypes.map((a) => a.id))
  if (!agents.has(state.agentType)) {
    throw new Error(`Unknown --agent "${state.agentType}". Use -h to list ids.`)
  }
  const providers = new Set(apiOptions.providers.map((p) => p.id))
  if (!providers.has(state.provider)) {
    throw new Error(`Unknown --provider "${state.provider}". Use -h to list ids.`)
  }
  const tools = new Set(apiOptions.tools.map((t) => t.id))
  for (const id of state.tools) {
    if (!tools.has(id)) throw new Error(`Unknown tool id in --tools: "${id}". Use -h to list.`)
  }
  const feats = new Map(apiOptions.features.map((f) => [f.id, f]))
  for (const id of state.features) {
    const f = feats.get(id)
    if (!f) throw new Error(`Unknown feature id in --features: "${id}". Use -h to list.`)
    if (!f.implemented) {
      throw new Error(`Feature "${id}" is not implemented in the generator.`)
    }
  }
}

function buildStateFromFlags(values, apiOptions) {
  const state = {
    artifact: String(values.artifact).trim(),
    agentType: String(values.agent).trim(),
    provider: String(values.provider).trim(),
    tools: splitCsv(values.tools),
    features: splitCsv(values.features),
    outPath: resolve(
      process.cwd(),
      values.output != null && values.output !== ''
        ? values.output
        : DEFAULT_AGENT_FILE
    ),
  }
  normalizeAfterAgentChange(state)
  validateBatchChoices(apiOptions, state)
  return state
}

async function promptArtifact(state) {
  state.artifact = await input({
    message: 'Project artifact (e.g. com.example.hello)',
    default: state.artifact || 'com.example.hello',
  })
}

async function promptAgentType(apiOptions, state) {
  state.agentType = await select({
    message: 'Agent template',
    choices: apiOptions.agentTypes.map((a) => ({
      name: `${a.label} — ${a.description}`,
      value: a.id,
    })),
    default: state.agentType,
  })
  normalizeAfterAgentChange(state)
}

async function promptProvider(apiOptions, state) {
  state.provider = await select({
    message: 'LLM provider',
    choices: apiOptions.providers.map((p) => ({
      name: p.envVar ? `${p.label} (${p.envVar})` : p.label,
      value: p.id,
      description: p.description,
    })),
    default: state.provider,
  })
}

async function promptTools(apiOptions, state) {
  if (isPlanner(state.agentType)) {
    state.tools = []
    return
  }
  state.tools = await checkbox({
    message:
      'Tools — ↑↓ move, space = toggle each option, enter = done (none selected = minimal agent)',
    choices: apiOptions.tools.map((t) => ({
      name: `${t.label} — ${t.description}`,
      value: t.id,
      checked: state.tools.includes(t.id),
    })),
  })
}

async function promptFeatures(apiOptions, state) {
  if (state.agentType !== 'BASIC') {
    state.features = []
    return
  }
  state.features = await checkbox({
    message:
      'Features (Basic only) — ↑↓ move, space = toggle, enter = done',
    choices: apiOptions.features
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

async function generate(url, state, { force, interactive }) {
  if (!isPlanner(state.agentType) && state.tools.length === 0 && interactive) {
    console.error(
      '\nNote: no tools selected — output will be a minimal agent (no ToolRegistry).\n'
    )
  }

  if (existsSync(state.outPath)) {
    if (interactive) {
      const ok = await confirm({
        message: `File exists: ${state.outPath}. Overwrite?`,
        default: false,
      })
      if (!ok) {
        console.error('Cancelled.')
        process.exit(0)
      }
    } else if (!force) {
      console.error(
        `Refusing to overwrite ${state.outPath} (use --force / -f for non-interactive overwrite).`
      )
      process.exit(1)
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

async function main() {
  const { values } = parseArgs({
    args: process.argv.slice(2),
    options: {
      help: { type: 'boolean', short: 'h' },
      url: { type: 'string' },
      artifact: { type: 'string', short: 'a' },
      agent: { type: 'string' },
      provider: { type: 'string', short: 'p' },
      tools: { type: 'string' },
      features: { type: 'string' },
      output: { type: 'string', short: 'o' },
      force: { type: 'boolean', short: 'f' },
    },
    allowPositionals: false,
    strict: true,
  })

  const url = baseUrlFromEnvAndArgs(values)

  if (values.help) {
    await printHelp(url)
    return
  }

  const hasBatch =
    values.artifact != null &&
    values.artifact !== '' &&
    values.agent != null &&
    values.agent !== '' &&
    values.provider != null &&
    values.provider !== ''

  if (hasBatch) {
    const apiOptions = await fetchOptions(url)
    const state = buildStateFromFlags(values, apiOptions)
    await generate(url, state, { force: values.force === true, interactive: false })
    return
  }

  const apiOptions = await fetchOptions(url)
  const state = {
    artifact: 'com.example.hello',
    agentType: apiOptions.agentTypes[0]?.id ?? 'BASIC',
    provider: apiOptions.providers[0]?.id ?? 'OPENAI',
    tools: [],
    features: [],
    outPath: resolve(process.cwd(), DEFAULT_AGENT_FILE),
  }

  await promptArtifact(state)
  await promptAgentType(apiOptions, state)
  await promptProvider(apiOptions, state)
  await promptTools(apiOptions, state)
  await promptFeatures(apiOptions, state)
  await promptOutputPath(state)

  await generate(url, state, { force: false, interactive: true })
}

main().catch((err) => {
  console.error(err.message || err)
  process.exit(1)
})
