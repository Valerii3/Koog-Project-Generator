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
        val provider = spec.llmProvider

        val files = mutableListOf(
            GeneratedFile("$base/settings.gradle.kts", renderSettingsGradle(spec)),
            GeneratedFile("$base/build.gradle.kts", renderBuildGradle(spec)),
            GeneratedFile("$base/gradle.properties", "kotlin.code.style=official\n"),
            GeneratedFile("$base/.env.example", renderEnvExample(spec)),
            GeneratedFile("$base/.gitignore", renderGitIgnore()),
            GeneratedFile("$base/README.md", renderReadme(spec)),
            GeneratedFile("$base/src/main/kotlin/${spec.packagePath}/Main.kt", renderMainKt(spec))
        )

        files += renderToolFiles(spec)

        return files
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
            ${if (spec.features.hasChatMemory) "implementation(\"ai.koog:agents-features-memory:0.7.1\")" else ""}
            ${if (spec.features.hasAgentPersistence) "implementation(\"ai.koog.agents:agents-features-snapshot:0.7.1\")" else ""}
            ${if (spec.features.hasTracing) "implementation(\"ai.koog:agents-features-trace:0.7.1\")" else ""}
            ${if (spec.features.hasTracing) "implementation(\"io.github.oshai:kotlin-logging-jvm:7.0.0\")" else ""}
            ${if (spec.features.hasTracing) "runtimeOnly(\"org.slf4j:slf4j-simple:2.0.16\")" else ""}
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
        source.addImports(provider.importLines)
        source.addImport("import kotlinx.coroutines.runBlocking")
        if (spec.tooling.enabled) {
            source.addImport("import ai.koog.agents.core.tools.ToolRegistry")
            source.addImport("import ${spec.packageName}.tools.createToolRegistry")
        }

        source.addDeclaration(renderAgentOptionsDataClass())
        source.addDeclaration(renderMainFunction(spec))
        source.addDeclaration(renderCreateAgentFunction(spec))

        return source.render()
    }

    private fun renderAgentOptionsDataClass(): String = """
        private data class AgentOptions(
            val systemPrompt: String? = null,
            val temperature: Double? = null,
            val maxIterations: Int? = null
        )
    """.trimIndent()

    private fun renderMainFunction(spec: ProjectSpec): String = buildString {
        val provider = spec.llmProvider
        appendLine("fun main() = runBlocking {")
        if (provider.requiresApiKey) {
            val envVar = requireNotNull(provider.envVarName)
            appendLine("    val apiKey: String? = System.getenv(\"$envVar\")")
            appendLine("        ?: error(\"Environment variable $envVar is not set.\")")
            appendLine()
        } else {
            appendLine("    val apiKey: String? = null")
            appendLine("    // Ollama does not require an API key.")
            appendLine()
        }
        appendLine()
        appendLine("    val options = AgentOptions(")
        appendLine("        systemPrompt = null,")
        appendLine("        temperature = null,")
        appendLine("        maxIterations = null")
        appendLine("    )")
        if (spec.tooling.enabled) {
            appendLine()
            appendLine("    val toolRegistry = createToolRegistry(apiKey)")
        }
        appendLine()
        val createAgentCall = if (spec.tooling.enabled) {
            if (provider.requiresApiKey) {
                "createAgent(requireNotNull(apiKey), options, toolRegistry)"
            } else {
                "createAgent(options, toolRegistry)"
            }
        } else {
            if (provider.requiresApiKey) {
                "createAgent(requireNotNull(apiKey), options)"
            } else {
                "createAgent(options)"
            }
        }
        appendLine("    val agent = $createAgentCall")
        if (spec.features.hasChatMemory) {
            appendLine("    val sessionId = \"${escapeKotlinString(spec.features.chatMemory.sessionId)}\"")
            appendLine("    val result = agent.run(\"Hello! Introduce yourself in one sentence.\", sessionId)")
        } else {
            appendLine("    val result = agent.run(\"Hello! Introduce yourself in one sentence.\")")
        }
        appendLine("    println(result)")
        append("}")
    }

    private fun renderCreateAgentFunction(spec: ProjectSpec): String = buildString {
        val provider = spec.llmProvider
        val signatureParts = mutableListOf<String>()
        if (provider.requiresApiKey) {
            signatureParts += "apiKey: String"
        }
        signatureParts += "options: AgentOptions"
        if (spec.tooling.enabled) {
            signatureParts += "toolRegistry: ToolRegistry"
        }
        appendLine("private fun createAgent(${signatureParts.joinToString(", ")}): AIAgent<String, String> {")
        provider.executorSetupLines.forEach { setupLine ->
            appendLine("    $setupLine")
        }
        if (provider.executorSetupLines.isNotEmpty()) {
            appendLine()
        }
        val hasFeatureInit = spec.features.hasEventHandler || spec.features.hasChatMemory || spec.features.hasAgentPersistence || spec.features.hasTracing
        if (hasFeatureInit) {
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
            appendLine("    }")
            appendLine()
        }
        appendLine("    return when {")
        append(renderAgentConstructorBlock(spec, condition = "options.temperature != null && options.maxIterations != null", includeTemperature = true, includeMaxIterations = true))
        appendLine()
        append(renderAgentConstructorBlock(spec, condition = "options.temperature != null", includeTemperature = true, includeMaxIterations = false))
        appendLine()
        append(renderAgentConstructorBlock(spec, condition = "options.maxIterations != null", includeTemperature = false, includeMaxIterations = true))
        appendLine()
        append(renderAgentConstructorBlock(spec, condition = "else", includeTemperature = false, includeMaxIterations = false))
        appendLine("    }")
        append("}")
    }

    private fun renderAgentConstructorBlock(
        spec: ProjectSpec,
        condition: String,
        includeTemperature: Boolean,
        includeMaxIterations: Boolean
    ): String = buildString {
        val provider = spec.llmProvider
        appendLine("        $condition -> AIAgent(")
        appendLine("            promptExecutor = ${provider.executorExpression},")
        appendLine("            llmModel = ${provider.modelExpression},")
        appendLine("            systemPrompt = options.systemPrompt,")
        if (includeTemperature) {
            appendLine("            temperature = options.temperature,")
        }
        if (includeMaxIterations) {
            appendLine("            maxIterations = options.maxIterations,")
        }
        if (spec.tooling.enabled) {
            appendLine("            toolRegistry = toolRegistry,")
        }
        val hasFeatureInit = spec.features.hasEventHandler || spec.features.hasChatMemory || spec.features.hasAgentPersistence || spec.features.hasTracing
        if (hasFeatureInit) {
            appendLine("            init = installFeatures")
        }
        appendLine("        )")
    }

    private fun renderToolFiles(spec: ProjectSpec): List<GeneratedFile> {
        if (!spec.tooling.enabled) {
            return emptyList()
        }
        val base = "${spec.projectName}/src/main/kotlin/${spec.packagePath}/tools"
        val out = mutableListOf(
            GeneratedFile("$base/ToolRegistry.kt", renderToolRegistryKt(spec))
        )
        if (spec.tooling.hasBuiltInTools) {
            out += GeneratedFile("$base/BuiltInTools.kt", renderBuiltInToolsKt(spec))
        }
        if (spec.tooling.hasAnnotationTools) {
            out += GeneratedFile("$base/AnnotationTools.kt", renderAnnotationToolsKt(spec))
        }
        if (spec.tooling.hasAgentAsTools) {
            out += GeneratedFile("$base/AgentAsToolRegistry.kt", renderAgentAsToolRegistryKt(spec))
            spec.tooling.agentAsTools.forEach { agentTool ->
                val fileName = "${toGeneratedAgentTypeName(agentTool.agentName)}Tool.kt"
                out += GeneratedFile(
                    "$base/agents/$fileName",
                    renderSingleAgentAsToolFile(spec, agentTool)
                )
            }
        }
        return out
    }

    private fun renderToolRegistryKt(spec: ProjectSpec): String = buildString {
        appendLine("package ${spec.packageName}.tools")
        appendLine()
        appendLine("import ai.koog.agents.core.tools.ToolRegistry")
        if (spec.tooling.hasBuiltInTools) {
            appendLine("import ${spec.packageName}.tools.createBuiltInToolRegistry")
        }
        if (spec.tooling.hasAnnotationTools) {
            appendLine("import ${spec.packageName}.tools.createAnnotationToolRegistry")
        }
        if (spec.tooling.hasAgentAsTools) {
            appendLine("import ${spec.packageName}.tools.createAgentAsToolRegistry")
        }
        appendLine()
        appendLine("fun createToolRegistry(apiKey: String? = null): ToolRegistry {")
        appendLine("    val registries = mutableListOf<ToolRegistry>()")
        if (spec.tooling.hasBuiltInTools) {
            appendLine("    registries += createBuiltInToolRegistry()")
        }
        if (spec.tooling.hasAnnotationTools) {
            appendLine("    registries += createAnnotationToolRegistry()")
        }
        if (spec.tooling.hasAgentAsTools) {
            appendLine("    registries += createAgentAsToolRegistry(apiKey)")
        }
        appendLine("    return registries.fold(ToolRegistry { }) { acc, item -> acc + item }")
        appendLine("}")
    }

    private fun renderBuiltInToolsKt(spec: ProjectSpec): String = buildString {
        appendLine("package ${spec.packageName}.tools")
        appendLine()
        appendLine("import ai.koog.agents.core.tools.ToolRegistry")
        appendLine("import ai.koog.agents.ext.tool.AskUser")
        appendLine("import ai.koog.agents.ext.tool.ExitTool")
        appendLine("import ai.koog.agents.ext.tool.SayToUser")
        appendLine("import ai.koog.agents.ext.tool.file.ListDirectoryTool")
        appendLine("import ai.koog.agents.ext.tool.file.ReadFileTool")
        appendLine("import ai.koog.agents.ext.tool.file.WriteFileTool")
        appendLine("import ai.koog.agents.ext.tool.file.jvm.JVMFileSystemProvider")
        appendLine()
        appendLine("fun createBuiltInToolRegistry(): ToolRegistry = ToolRegistry {")
        appendLine("    tool(SayToUser)")
        appendLine("    tool(AskUser)")
        appendLine("    tool(ExitTool)")
        appendLine("    tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))")
        appendLine("    tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))")
        appendLine("    tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))")
        appendLine("}")
    }

    private fun renderAnnotationToolsKt(spec: ProjectSpec): String = buildString {
        appendLine("package ${spec.packageName}.tools")
        appendLine()
        appendLine("import ai.koog.agents.core.tools.ToolRegistry")
        appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription")
        appendLine("import ai.koog.agents.core.tools.annotations.Tool")
        appendLine("import ai.koog.agents.core.tools.reflect.ToolSet")
        appendLine("import ai.koog.agents.core.tools.reflect.asTools")
        appendLine()
        appendLine("fun createAnnotationToolRegistry(): ToolRegistry = ToolRegistry {")
        appendLine("    tools(${spec.tooling.annotationToolSetClassName}().asTools())")
        appendLine("}")
        appendLine()
        append(renderAnnotationToolSetClass(spec.tooling))
    }

    private fun renderAgentAsToolRegistryKt(spec: ProjectSpec): String = buildString {
        appendLine("package ${spec.packageName}.tools")
        appendLine()
        appendLine("import ai.koog.agents.core.tools.ToolRegistry")
        spec.tooling.agentAsTools.forEach { agentTool ->
            appendLine("import ${spec.packageName}.tools.agents.create${toGeneratedAgentTypeName(agentTool.agentName)}Tool")
        }
        appendLine()
        appendLine("fun createAgentAsToolRegistry(apiKey: String? = null): ToolRegistry = ToolRegistry {")
        spec.tooling.agentAsTools.forEach { agentTool ->
            appendLine("    tool(create${toGeneratedAgentTypeName(agentTool.agentName)}Tool(apiKey))")
        }
        appendLine("}")
    }

    private fun renderSingleAgentAsToolFile(spec: ProjectSpec, tool: AgentAsToolSpec): String = buildString {
        val provider = spec.llmProvider
        val agentTypeName = toGeneratedAgentTypeName(tool.agentName)
        val funcName = "create${agentTypeName}Tool"
        val nestedRegistryFunction = "create${agentTypeName}NestedToolRegistry"
        val systemPromptConst = "${agentTypeName.uppercase()}_SYSTEM_PROMPT"
        appendLine("package ${spec.packageName}.tools.agents")
        appendLine()
        appendLine("import ai.koog.agents.core.agent.AIAgentService")
        appendLine("import ai.koog.agents.core.agent.createAgentTool")
        appendLine("import ai.koog.agents.core.tools.ToolRegistry")
        provider.importLines.forEach { appendLine(it) }
        appendLine()
        appendLine("private const val $systemPromptConst = \"${escapeKotlinString(tool.systemPrompt)}\"")
        appendLine()
        appendLine("private fun $nestedRegistryFunction(): ToolRegistry = ToolRegistry {")
        appendLine("    // TODO: Add nested-agent specific tools here (built-in, annotation-based, or agent tools).")
        appendLine("}")
        appendLine()
        appendLine("fun $funcName(rawApiKey: String? = null) = run {")
        if (provider.requiresApiKey) {
            val envVar = requireNotNull(provider.envVarName)
            appendLine("    val apiKey = requireNotNull(rawApiKey) { \"Environment variable $envVar is required.\" }")
        } else {
            appendLine("    // Provider ${provider.displayName} does not require an API key.")
        }
        provider.executorSetupLines.forEach { setupLine ->
            appendLine("    $setupLine")
        }
        appendLine()
        appendLine("    val service = AIAgentService(")
        appendLine("        promptExecutor = ${provider.executorExpression},")
        appendLine("        llmModel = ${provider.modelExpression},")
        appendLine("        systemPrompt = $systemPromptConst,")
        appendLine("        toolRegistry = $nestedRegistryFunction()")
        appendLine("    )")
        appendLine("    // TODO: Add nested-agent feature setup in this file when needed")
        appendLine("    // (for example event handling, memory, persistence, tracing).")
        appendLine()
        appendLine("    service.createAgentTool(")
        appendLine("        agentName = \"${escapeKotlinString(tool.agentName)}\",")
        appendLine("        agentDescription = \"${escapeKotlinString(tool.agentDescription)}\",")
        appendLine("        inputDescription = \"${escapeKotlinString(tool.inputDescription)}\"")
        appendLine("    )")
        appendLine("}")
    }

    private fun renderAnnotationToolSetClass(tooling: ProjectToolingSpec): String = buildString {
        appendLine("@LLMDescription(\"User-defined annotation-based tools\")")
        appendLine("class ${tooling.annotationToolSetClassName} : ToolSet {")

        val tools = if (tooling.annotationTools.isEmpty()) {
            listOf(
                AnnotationToolSpec(
                    functionName = "myTool",
                    customName = null,
                    description = "TODO: describe what this tool does",
                    parameters = emptyList()
                )
            )
        } else {
            tooling.annotationTools
        }

        tools.forEachIndexed { index, tool ->
            if (index > 0) {
                appendLine()
            }
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
            appendLine("    @Tool(customName = \"${escapeKotlinString(tool.customName)}\")")
        }
        appendLine("    @LLMDescription(\"$escapedDescription\")")

        if (tool.parameters.isEmpty()) {
            appendLine("    fun ${tool.functionName}(): String {")
        } else {
            appendLine("    fun ${tool.functionName}(")
            tool.parameters.forEachIndexed { index, parameter ->
                val suffix = if (index == tool.parameters.lastIndex) "" else ","
                appendLine("        @LLMDescription(\"${escapeKotlinString(parameter.description)}\")")
                appendLine("        ${parameter.name}: ${parameter.type.kotlinType}$suffix")
            }
            appendLine("    ): String {")
        }

        appendLine("        TODO(\"Implement tool '${tool.functionName}'\")")
        append("    }")
    }

    private fun renderEnvExample(spec: ProjectSpec): String {
        val provider = spec.llmProvider
        return if (provider.requiresApiKey) {
            val envVar = requireNotNull(provider.envVarName)
            """
                # Copy to .env and set your key for ${provider.displayName}
                $envVar=your_api_key_here
            """.trimIndent() + "\n"
        } else {
            """
                # Ollama runs locally and does not require an API key.
                # Ensure Ollama is running and the selected model is available.
            """.trimIndent() + "\n"
        }
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
        appendLine("1. Copy `.env.example` to `.env` (optional, for your own local workflow).")
        if (spec.llmProvider.requiresApiKey) {
            val envVar = requireNotNull(spec.llmProvider.envVarName)
            appendLine("2. Export your API key:")
            appendLine()
            appendLine("   ```bash")
            appendLine("   export $envVar=...")
            appendLine("   ```")
        } else {
            appendLine("2. Start Ollama locally and make sure `llama3.2` is available.")
        }
        appendLine()
        appendLine("3. Run the app:")
        appendLine()
        appendLine("   ```bash")
        appendLine("   ./gradlew run")
        appendLine("   ```")
        appendLine()
        appendLine("## What this project includes")
        appendLine()
        appendLine("- Constructor-based `AIAgent` setup (no `AIAgentConfig`)")
        appendLine("- LLM provider: ${spec.llmProvider.displayName}")
        appendLine("- Prompt executor + model configuration")
        appendLine("- Optional system prompt")
        appendLine("- Optional temperature")
        appendLine("- Optional max iterations")
        if (spec.features.hasEventHandler) {
            appendLine("- Event handler callbacks via `handleEvents`")
        }
        if (spec.features.hasChatMemory) {
            appendLine("- In-memory chat memory via `install(ChatMemory)` with `windowSize(${spec.features.chatMemory.windowSize})`")
            appendLine("- Session-aware runs via `agent.run(message, \"${escapeKotlinString(spec.features.chatMemory.sessionId)}\")`")
            appendLine("- Adds dependency `ai.koog:agents-features-memory:0.7.1`")
        }
        if (spec.features.hasAgentPersistence) {
            appendLine("- Agent persistence via `install(Persistence)`")
            appendLine("- Uses `InMemoryPersistenceStorageProvider()`")
            appendLine("- Sets `enableAutomaticPersistence = false`")
            appendLine("- Adds dependency `ai.koog.agents:agents-features-snapshot:0.7.1`")
        }
        if (spec.features.hasTracing) {
            appendLine("- Tracing via `install(Tracing)`")
            appendLine("- Adds `TraceFeatureMessageLogWriter(logger)`")
            appendLine("- Adds dependencies `ai.koog:agents-features-trace:0.7.1` and Kotlin logging")
        }

        if (spec.tooling.enabled) {
            appendLine("- Tool registry wiring in `src/main/kotlin/${spec.packagePath}/tools/ToolRegistry.kt`")
            if (spec.tooling.hasBuiltInTools) {
                appendLine("- Built-in tools enabled in `BuiltInTools.kt` (chat + file tools)")
            }
            if (spec.tooling.hasAnnotationTools) {
                appendLine("- Annotation-based tool stubs with TODO implementations in `AnnotationTools.kt`")
            }
            if (spec.tooling.hasAgentAsTools) {
                appendLine("- Agent-as-tool registry in `AgentAsToolRegistry.kt`")
                appendLine("- One file per nested agent tool under `tools/agents/`, each with nested tool-registry and feature extension hooks")
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

    private fun toGeneratedAgentTypeName(value: String): String {
        val pascal = toPascalCase(value)
        return if (pascal.endsWith("Agent")) pascal else "${pascal}Agent"
    }
}
