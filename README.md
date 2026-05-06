# Koog Starter Generator

Standalone CLI tool that generates a ZIP archive containing a Kotlin/JVM Koog starter project.

## Run the CLI

```bash
cd koog-starter-generator
./gradlew run
```

If you do not have a Gradle wrapper in this folder yet, run with local Gradle:

```bash
gradle run
```

## Generate a ZIP

The CLI asks for:

- LLM provider
- project name
- output path
- package name
- agent template type
- tool options (multi-select)
- agent features (iterative add/reconfigure menu)

Provider options:

- OpenAI
- Anthropic
- Google
- DeepSeek
- OpenRouter
- Bedrock
- Mistral
- Ollama

Tool options currently include:

- add built-in tools
- create annotation-based tool stubs (with TODO implementations)
- create nested agents as tools (one generated file per nested agent, with nested tool/feature extension hooks)

Feature options currently include:

- event handler (`handleEvents` callbacks in generated `AIAgent`)
- chat memory (in-memory `install(ChatMemory)` with configurable `windowSize` and `sessionId`)
- agent persistence (`install(Persistence)` with `InMemoryPersistenceStorageProvider` and `enableAutomaticPersistence = false`)
- tracing (`install(Tracing)` with `addMessageProcessor(TraceFeatureMessageLogWriter(logger))`)

Then it writes a ZIP archive to your chosen output path.

## HTTP CLI (Node, talks to the backend)

The CLI calls the same `/api` as the web UI. By default it uses production:

`https://koog-project-generator-production.up.railway.app`

It walks through artifact → agent → provider → tools → features (planner agents skip tools/features; only Basic agents get features).

**Run from the repo:**

```bash
cd koog-gen-cli
npm install
node index.mjs
```

**Local backend instead of production:**

```bash
node index.mjs --url http://127.0.0.1:8080
# or: KOOG_GENERATOR_URL=http://127.0.0.1:8080 node index.mjs
```

After `npm install`, you can use `npx koog-gen` from `koog-gen-cli/` (or `npm link` there). Publishing to npm is optional; see checklist below if you want `npx` without cloning this repo.

### Checklist: publish `npx` for others

1. **npm account** — [create one](https://www.npmjs.com/signup) and `npm login` on your machine.
2. **Unique package name** — in `koog-gen-cli/package.json`, set `"name"` to something available on npm (e.g. `@your-username/koog-gen`). Scoped public packages need `npm publish --access public`.
3. **Remove `"private": true`** (or set `"private": false`) if you want a public package.
4. **Version** — bump `"version"` when you release (e.g. `0.1.0` → `0.1.1`).
5. **Publish** — from `koog-gen-cli/`: `npm publish` (or `npm publish --access public` for a scoped name).
6. **Users run** — `npx <your-package-name>` (they get the hardcoded Railway URL unless they pass `--url` / `KOOG_GENERATOR_URL`).
7. **When you change Railway URL** — update `DEFAULT_GENERATOR_URL` in `koog-gen-cli/index.mjs`, bump version, publish again.

## Unpack and run generated Koog project

```bash
cd <output-path>
unzip <project-name>-basic-agent.zip
cd <project-name>
cp .env.example .env
# export OPENAI_API_KEY=... (or load from .env)
./gradlew run
```
