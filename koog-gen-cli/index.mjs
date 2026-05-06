#!/usr/bin/env node
import { writeFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { select, input, checkbox, confirm } from '@inquirer/prompts'

/** Default API origin (Railway production). Override with --url or KOOG_GENERATOR_URL for local/staging. */
const DEFAULT_GENERATOR_URL = 'https://koog-project-generator-production.up.railway.app'

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

function zipNameFromArtifact(artifact) {
  const parts = artifact.trim().split('.')
  return `${parts[parts.length - 1] || 'project'}.zip`
}

function filenameFromContentDisposition(header, fallback) {
  if (!header) return fallback
  const quoted = /filename="([^"]+)"/i.exec(header)
  if (quoted) return quoted[1]
  const plain = /filename=([^;\s]+)/i.exec(header)
  if (plain) return plain[1].replace(/^"|"$/g, '')
  const star = /filename\*=UTF-8''([^;\s]+)/i.exec(header)
  if (star) return decodeURIComponent(star[1])
  return fallback
}

async function main() {
  const { url } = parseArgs(process.argv.slice(2))
  console.error(`Generator base URL: ${url}\n`)

  const options = await fetchOptions(url)

  const artifact = await input({
    message: 'Project artifact (e.g. com.example.hello)',
    default: 'com.example.hello',
  })

  const agentType = await select({
    message: 'Agent template',
    choices: options.agentTypes.map((a) => ({
      name: `${a.label} — ${a.description}`,
      value: a.id,
    })),
  })

  const provider = await select({
    message: 'LLM provider',
    choices: options.providers.map((p) => ({
      name: p.envVar ? `${p.label} (${p.envVar})` : p.label,
      value: p.id,
      description: p.description,
    })),
  })

  let tools = []
  if (!isPlanner(agentType)) {
    tools = await checkbox({
      message: 'Tools (toggle with space, confirm with enter)',
      choices: options.tools.map((t) => ({
        name: `${t.label} — ${t.description}`,
        value: t.id,
        checked: false,
      })),
    })
  }

  let features = []
  if (agentType === 'BASIC') {
    features = await checkbox({
      message: 'Features (Basic agent only)',
      choices: options.features
        .filter((f) => f.implemented)
        .map((f) => ({
          name: `${f.label} — ${f.description}`,
          value: f.id,
          checked: false,
        })),
    })
  }

  const defaultZip = zipNameFromArtifact(artifact)
  const outRaw = await input({
    message: 'Output ZIP path',
    default: resolve(process.cwd(), defaultZip),
  })
  const outPath = resolve(outRaw)

  if (existsSync(outPath)) {
    const ok = await confirm({
      message: `File exists: ${outPath}. Overwrite?`,
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
      artifact: artifact.trim(),
      agentType,
      provider,
      tools,
      features,
    }),
  })

  if (!res.ok) {
    console.error(await res.text())
    process.exit(1)
  }

  const buf = Buffer.from(await res.arrayBuffer())
  const cd = res.headers.get('content-disposition')
  const suggested = filenameFromContentDisposition(cd, defaultZip)

  writeFileSync(outPath, buf)
  console.error(`\nWrote ${outPath} (${buf.length} bytes)`)
  if (suggested && suggested !== defaultZip) {
    console.error(`(Server suggested filename: ${suggested})`)
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
