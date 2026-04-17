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

## Unpack and run generated Koog project

```bash
cd <output-path>
unzip <project-name>-basic-agent.zip
cd <project-name>
cp .env.example .env
# export OPENAI_API_KEY=... (or load from .env)
./gradlew run
```
