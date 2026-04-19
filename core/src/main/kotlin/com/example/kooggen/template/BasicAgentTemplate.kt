package com.example.kooggen.template

import com.example.kooggen.model.AgentTemplateType
import com.example.kooggen.model.AgentAsToolSpec
import com.example.kooggen.model.AnnotationToolSpec
import com.example.kooggen.model.ProjectSpec
import com.example.kooggen.model.ProjectToolingSpec

class BasicAgentTemplate : ProjectTemplate {
    override val type: AgentTemplateType = AgentTemplateType.BASIC

    override fun render(spec: ProjectSpec): List<GeneratedFile> {
        val base = spec.projectName
        return listOf(
            GeneratedFile("$base/settings.gradle.kts", renderSettingsGradle(spec)),
            GeneratedFile("$base/build.gradle.kts", renderBuildGradle(spec)),
            GeneratedFile("$base/gradle.properties", "kotlin.code.style=official\n"),
            GeneratedFile("$base/.gitignore", renderGitIgnore()),
            GeneratedFile("$base/README.md", renderReadme(spec)),
            GeneratedFile("$base/src/main/kotlin/${spec.packagePath}/Main.kt", renderMainKt(spec))
        )
    }

    private fun renderSettingsGradle(spec: ProjectSpec): String =
        "rootProject.name = \"${spec.projectName}\"\n"

    private fun renderBuildGradle(spec: ProjectSpec): String = """
        plugins {
            kotlin("jvm") version "2.0.21"
            application
        }

        group = "${spec.packageName}"
        version = "0.1.0"

        repositories {
            mavenCentral()
        }

        dependencies {
            implementation("ai.koog:koog-agents:0.7.1")
            ${if (spec.features.hasChatMemory || spec.features.hasLongTermMemory) "implementation(\"ai.koog:agents-features-memory:0.7.1\")" else ""}
            ${if (spec.features.hasAgentPersistence) "implementation(\"ai.koog.agents:agents-features-snapshot:0.7.1\")" else ""}
            ${if (spec.features.hasTracing) "implementation(\"ai.koog:agents-features-trace:0.7.1\")" else ""}
            ${if (spec.features.hasTracing) "implementation(\"io.github.oshai:kotlin-logging-jvm:7.0.0\")" else ""}
            ${if (spec.features.hasTracing) "runtimeOnly(\"org.slf4j:slf4j-simple:2.0.16\")" else ""}
            ${if (spec.tooling.hasMcpTools) "implementation(\"ai.koog:agents-mcp:0.7.1\")" else ""}
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        }

        application {
            mainClass.set("${spec.packageName}.MainKt")
        }

        kotlin {
            jvmToolchain(17)
        }
    """.trimIndent() + "\n"

    private fun renderMainKt(spec: ProjectSpec): String {
        val provider = spec.llmProvider
        val source = KotlinSourceFile(spec.packageName)

        source.addImport("import ai.koog.agents.core.agent.AIAgent")
        if (spec.features.hasEventHandler) {
            source.addImport("import ai.koog.agents.features.eventHandler.feature.handleEvents")
        }
        if (spec.features.hasChatMemory) {
            source.addImport("import ai.koog.agents.features.memory.feature.ChatMemory")
        }
        if (spec.features.hasAgentPersistence) {
            source.addImport("import ai.koog.agents.snapshot.feature.Persistence")
            source.addImport("import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider")
        }
        if (spec.features.hasTracing) {
            source.addImport("import ai.koog.agents.features.tracing.feature.Tracing")
            source.addImport("import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter")
            source.addImport("import io.github.oshai.kotlinlogging.KotlinLogging")
        }
        if (spec.features.hasLongTermMemory) {
            source.addImport("import ai.koog.agents.core.annotation.ExperimentalAgentsApi")
            source.addImport("import ai.koog.agents.features.memory.feature.LongTermMemory")
            source.addImport("import ai.koog.agents.features.memory.storage.InMemoryRecordStorage")
            source.addImport("import ai.koog.agents.features.memory.search.SimilaritySearchStrategy")
        }
        if (spec.features.hasAnyOpenTelemetry) {
            source.addImport("import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry")
        }
        source.addImports(provider.importLines)
        source.addImport("import kotlinx.coroutines.runBlocking")

        if (spec.tooling.enabled) {
            source.addImport("import ai.koog.agents.core.tools.ToolRegistry")
            if (spec.tooling.hasBuiltInTools) {
                source.addImport("import ai.koog.agents.ext.tool.AskUser")
                source.addImport("import ai.koog.agents.ext.tool.ExitTool")
                source.addImport("import ai.koog.agents.ext.tool.SayToUser")
                source.addImport("import ai.koog.agents.ext.tool.file.ListDirectoryTool")
                source.addImport("import ai.koog.agents.ext.tool.file.ReadFileTool")
                source.addImport("import ai.koog.agents.ext.tool.file.WriteFileTool")
                source.addImport("import ai.koog.agents.ext.tool.file.jvm.JVMFileSystemProvider")
            }
            if (spec.tooling.hasAnnotationTools) {
                source.addImport("import ai.koog.agents.core.tools.annotations.LLMDescription")
                source.addImport("import ai.koog.agents.core.tools.annotations.Tool")
                source.addImport("import ai.koog.agents.core.tools.reflect.ToolSet")
                source.addImport("import ai.koog.agents.core.tools.reflect.asTools")
            }
            if (spec.tooling.hasAgentAsTools) {
                source.addImport("import ai.koog.agents.core.agent.AIAgentService")
                source.addImport("import ai.koog.agents.core.agent.createAgentTool")
                source.addImport("import ai.koog.agents.core.tools.reflect.typeToken")
            }
        }

        if (provider.requiresApiKey) {
            source.addDeclaration(renderTopLevelApiKey(provider))
        }
        provider.executorSetupLines.forEach { setupLine ->
            source.addDeclaration("private $setupLine")
        }

        if (spec.tooling.hasBuiltInTools) {
            source.addDeclaration(renderBuiltInToolRegistryFunction())
        }
        if (spec.tooling.hasAnnotationTools) {
            source.addDeclaration(renderSampleToolSetClass(spec.tooling))
        }
        if (spec.tooling.hasAgentAsTools) {
            spec.tooling.agentAsTools.forEach { agentTool ->
                source.addDeclaration(renderAgentAsToolVals(spec, agentTool))
            }
        }

        source.addDeclaration(renderMainFunction(spec))

        return source.render()
    }

    private fun renderTopLevelApiKey(provider: com.example.kooggen.model.LlmProvider): String {
        val envVar = requireNotNull(provider.envVarName)
        return """
            private val apiKey: String = System.getenv("$envVar")
                ?: error("Environment variable $envVar is not set.")
        """.trimIndent()
    }

    private fun renderMainFunction(spec: ProjectSpec): String = buildString {
        val provider = spec.llmProvider
        appendLine("fun main() = runBlocking {")

        if (spec.tooling.enabled) {
            append(renderToolRegistryBlock(spec))
            appendLine()
        }

        val hasFeatureInit = spec.features.hasEventHandler || spec.features.hasChatMemory ||
            spec.features.hasAgentPersistence || spec.features.hasTracing ||
            spec.features.hasLongTermMemory || spec.features.hasAnyOpenTelemetry
        if (hasFeatureInit) {
            append(renderFeatureInitBlock(spec))
            appendLine()
        }

        appendLine("    val agent = AIAgent<String, String>(")
        appendLine("        promptExecutor = ${provider.executorExpression},")
        appendLine("        llmModel = ${provider.modelExpression},")
        appendLine("        systemPrompt = \"You are a helpful assistant\",")
        appendLine("        temperature = 0.7,")
        appendLine("        maxIterations = 10,")
        if (spec.tooling.enabled) {
            appendLine("        toolRegistry = toolRegistry,")
        }
        if (hasFeatureInit) {
            appendLine("        init = installFeatures,")
        }
        appendLine("    )")
        appendLine()

        if (spec.features.hasChatMemory) {
            appendLine("    val sessionId = \"${escapeKotlinString(spec.features.chatMemory.sessionId)}\"")
            appendLine("    val result = agent.run(\"Hello! Introduce yourself in one sentence.\", sessionId)")
        } else {
            appendLine("    val result = agent.run(\"Hello! Introduce yourself in one sentence.\")")
        }
        appendLine("    println(result)")
        append("}")
    }

    private fun renderToolRegistryBlock(spec: ProjectSpec): String = buildString {
        val builtInPart = if (spec.tooling.hasBuiltInTools) "createBuiltInToolRegistry()" else null

        val dslLines = mutableListOf<String>()
        if (spec.tooling.hasAnnotationTools) {
            dslLines += "        tools(${spec.tooling.annotationToolSetClassName}().asTools())"
        }
        if (spec.tooling.hasAgentAsTools) {
            spec.tooling.agentAsTools.forEach { agentTool ->
                dslLines += "        tool(${toAgentToolValName(agentTool.agentName)})"
            }
        }

        val dslPart = if (dslLines.isNotEmpty()) buildString {
            appendLine("ToolRegistry {")
            dslLines.forEach { appendLine(it) }
            append("    }")
        } else null

        val parts = listOfNotNull(builtInPart, dslPart)

        if (parts.isEmpty()) {
            append("    val toolRegistry = ToolRegistry { }")
        } else {
            append("    val toolRegistry = ${parts.joinToString(" + ")}")
        }
        appendLine()
    }

    private fun renderFeatureInitBlock(spec: ProjectSpec): String = buildString {
        if (spec.features.hasLongTermMemory) {
            appendLine("    @OptIn(ExperimentalAgentsApi::class)")
            appendLine("    val longTermMemoryStorage = InMemoryRecordStorage()")
            appendLine()
        }
        appendLine("    val installFeatures: AIAgent<String, String>.() -> Unit = {")
        if (spec.features.hasTracing) {
            appendLine("        val logger = KotlinLogging.logger(\"koog.tracing\")")
            appendLine("        install(Tracing) {")
            appendLine("            addMessageProcessor(TraceFeatureMessageLogWriter(logger))")
            appendLine("        }")
        }
        if (spec.features.hasEventHandler) {
            appendLine("        handleEvents {")
            if (spec.features.eventHandler.includeOnToolCallStarting) {
                appendLine("            onToolCallStarting { eventContext ->")
                appendLine("                println(\"Tool called: ${'$'}{eventContext.toolName} with args ${'$'}{eventContext.toolArgs}\")")
                appendLine("            }")
            }
            if (spec.features.eventHandler.includeOnAgentCompleted) {
                appendLine("            onAgentCompleted { eventContext ->")
                appendLine("                println(\"Agent finished with result: ${'$'}{eventContext.result}\")")
                appendLine("            }")
            }
            appendLine("        }")
        }
        if (spec.features.hasChatMemory) {
            appendLine("        install(ChatMemory) {")
            appendLine("            windowSize(${spec.features.chatMemory.windowSize})")
            appendLine("        }")
        }
        if (spec.features.hasAgentPersistence) {
            appendLine("        install(Persistence) {")
            appendLine("            storage = InMemoryPersistenceStorageProvider()")
            appendLine("            enableAutomaticPersistence = false")
            appendLine("        }")
        }
        if (spec.features.hasLongTermMemory) {
            appendLine("        @OptIn(ExperimentalAgentsApi::class)")
            appendLine("        install(LongTermMemory) {")
            appendLine("            retrieval {")
            appendLine("                storage = longTermMemoryStorage")
            appendLine("                searchStrategy = SimilaritySearchStrategy(topK = ${spec.features.longTermMemory.topK})")
            appendLine("            }")
            appendLine("            ingestion {")
            appendLine("                storage = longTermMemoryStorage")
            appendLine("                enableAutomaticIngestion = ${spec.features.longTermMemory.enableAutomaticIngestion}")
            appendLine("            }")
            appendLine("        }")
        }
        if (spec.features.hasAnyOpenTelemetry) {
            appendLine("        install(OpenTelemetry) {")
            if (spec.features.hasDatadogExporter) {
                appendLine("            addDatadogExporter()")
            }
            if (spec.features.hasLangfuseExporter) {
                appendLine("            addLangfuseExporter()")
            }
            if (spec.features.hasWeaveExporter) {
                appendLine("            addWeaveExporter()")
            }
            appendLine("        }")
        }
        append("    }")
    }

    private fun renderBuiltInToolRegistryFunction(): String = """
        fun createBuiltInToolRegistry(): ToolRegistry = ToolRegistry {
            tool(SayToUser)
            tool(AskUser)
            tool(ExitTool)
            tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
            tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))
        }
    """.trimIndent()

    private fun renderSampleToolSetClass(tooling: ProjectToolingSpec): String = buildString {
        appendLine("@LLMDescription(\"User-defined annotation-based tools\")")
        appendLine("class ${tooling.annotationToolSetClassName} : ToolSet {")

        val tools = if (tooling.annotationTools.isEmpty()) {
            listOf(
                AnnotationToolSpec(
                    functionName = "sampleTool",
                    customName = "sample_tool",
                    description = "Sample tool description",
                    parameters = emptyList()
                )
            )
        } else {
            tooling.annotationTools
        }

        tools.forEachIndexed { index, tool ->
            if (index > 0) appendLine()
            append(renderAnnotationToolFunction(tool))
        }

        appendLine()
        append("}")
    }

    private fun renderAnnotationToolFunction(tool: AnnotationToolSpec): String = buildString {
        val escapedDescription = escapeKotlinString(tool.description)
        if (tool.customName.isNullOrBlank()) {
            appendLine("    @Tool")
        } else {
            appendLine("    @Tool(\"${escapeKotlinString(tool.customName)}\")")
        }
        appendLine("    @LLMDescription(\"$escapedDescription\")")

        val params = if (tool.parameters.isEmpty()) {
            listOf(
                com.example.kooggen.model.ToolParameterSpec(
                    name = "input",
                    type = com.example.kooggen.model.ToolParameterType.STRING,
                    description = "Sample tool input"
                )
            )
        } else {
            tool.parameters
        }

        appendLine("    fun ${tool.functionName}(")
        params.forEachIndexed { index, parameter ->
            val suffix = if (index == params.lastIndex) "" else ","
            appendLine("        @LLMDescription(\"${escapeKotlinString(parameter.description)}\")")
            appendLine("        ${parameter.name}: ${parameter.type.kotlinType}$suffix")
        }
        appendLine("    ): String {")
        appendLine("        TODO(\"Not yet implemented\")")
        append("    }")
    }

    private fun renderAgentAsToolVals(spec: ProjectSpec, tool: AgentAsToolSpec): String = buildString {
        val provider = spec.llmProvider
        val serviceVar = toAgentServiceValName(tool.agentName)
        val toolVar = toAgentToolValName(tool.agentName)
        val systemPrompt = tool.systemPrompt.ifBlank { "You are a specialized agent." }
        val agentNameLiteral = tool.agentName.ifBlank { "exampleAgent" }

        appendLine("val $serviceVar = AIAgentService(")
        appendLine("    promptExecutor = ${provider.executorExpression},")
        appendLine("    llmModel = ${provider.modelExpression},")
        appendLine("    systemPrompt = \"${escapeKotlinString(systemPrompt)}\",")
        appendLine(")")
        appendLine()
        appendLine("val $toolVar = $serviceVar.createAgentTool(")
        appendLine("    agentName = \"${escapeKotlinString(agentNameLiteral)}\",")
        appendLine("    agentDescription = \"${escapeKotlinString(tool.agentDescription)}\",")
        appendLine("    inputDescription = \"${escapeKotlinString(tool.inputDescription)}\",")
        appendLine("    inputType = typeToken<String>(),")
        append(")")
    }

    private fun renderReadme(spec: ProjectSpec): String = buildString {
        appendLine("# ${spec.projectName}")
        appendLine()
        appendLine("Minimal Koog basic-agent starter generated by `koog-starter-generator`.")
        appendLine()
        appendLine("## Requirements")
        appendLine()
        appendLine("- JDK 17+")
        appendLine("- Gradle 8+")
        appendLine()
        appendLine("## Setup")
        appendLine()
        if (spec.llmProvider.requiresApiKey) {
            val envVar = requireNotNull(spec.llmProvider.envVarName)
            appendLine("1. Export your API key:")
            appendLine()
            appendLine("   ```bash")
            appendLine("   export $envVar=...")
            appendLine("   ```")
        } else {
            appendLine("1. Start Ollama locally and make sure `llama3.2` is available.")
        }
        appendLine()
        appendLine("2. Run the app:")
        appendLine()
        appendLine("   ```bash")
        appendLine("   ./gradlew run")
        appendLine("   ```")
        appendLine()
        appendLine("## What this project includes")
        appendLine()
        appendLine("- `AIAgent` constructor with `systemPrompt`, `temperature = 0.7`, `maxIterations = 10`")
        appendLine("- LLM provider: ${spec.llmProvider.displayName}")
        if (spec.features.hasEventHandler) {
            appendLine("- Event handler callbacks via `handleEvents`")
        }
        if (spec.features.hasChatMemory) {
            appendLine("- In-memory chat memory via `install(ChatMemory)` with `windowSize(${spec.features.chatMemory.windowSize})`")
            appendLine("- Session-aware runs via `agent.run(message, \"${escapeKotlinString(spec.features.chatMemory.sessionId)}\")`")
        }
        if (spec.features.hasAgentPersistence) {
            appendLine("- Agent persistence via `install(Persistence)` with `InMemoryPersistenceStorageProvider()`")
        }
        if (spec.features.hasTracing) {
            appendLine("- Tracing via `install(Tracing)` with `TraceFeatureMessageLogWriter(logger)`")
        }
        if (spec.tooling.enabled) {
            appendLine("- All tool wiring is inlined in `Main.kt`")
            if (spec.tooling.hasBuiltInTools) {
                appendLine("- Built-in tools via `createBuiltInToolRegistry()` (chat + file tools)")
            }
            if (spec.tooling.hasAnnotationTools) {
                appendLine("- Annotation-based tools via `${spec.tooling.annotationToolSetClassName}` with TODO stubs")
            }
            if (spec.tooling.hasAgentAsTools) {
                appendLine("- Agent-as-tool vals declared at top level in `Main.kt`")
            }
        }
    }

    private fun renderGitIgnore(): String = """
        .gradle/
        build/
        .idea/
        *.iml
        .env
    """.trimIndent() + "\n"

    private fun escapeKotlinString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    private fun toPascalCase(value: String): String = value
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .joinToString("") { part ->
            part.replaceFirstChar { ch -> ch.uppercase() }
        }
        .ifBlank { "Agent" }

    private fun toCamelCase(value: String): String {
        val pascal = toPascalCase(value)
        return pascal.replaceFirstChar { ch -> ch.lowercase() }
    }

    private fun toAgentServiceValName(agentName: String): String {
        val camel = toCamelCase(agentName.ifBlank { "exampleAgent" })
        val base = if (camel.endsWith("Agent")) camel else "${camel}Agent"
        return "${base}Service"
    }

    private fun toAgentToolValName(agentName: String): String {
        val camel = toCamelCase(agentName.ifBlank { "exampleAgent" })
        val base = if (camel.endsWith("Agent")) camel else "${camel}Agent"
        return "${base}Tool"
    }
}
