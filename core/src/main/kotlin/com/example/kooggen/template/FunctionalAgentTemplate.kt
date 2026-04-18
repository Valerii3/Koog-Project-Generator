package com.example.kooggen.template

import com.example.kooggen.model.AgentTemplateType
import com.example.kooggen.model.AgentAsToolSpec
import com.example.kooggen.model.AnnotationToolSpec
import com.example.kooggen.model.ProjectSpec
import com.example.kooggen.model.ProjectToolingSpec

class FunctionalAgentTemplate : ProjectTemplate {
    override val type: AgentTemplateType = AgentTemplateType.FUNCTIONAL

    override fun render(spec: ProjectSpec): List<GeneratedFile> {
        val base = spec.projectName

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
        source.addImport("import ai.koog.agents.local.strategy.functionalStrategy")
        source.addImports(provider.importLines)
        source.addImport("import kotlinx.coroutines.runBlocking")
        if (spec.tooling.enabled) {
            source.addImport("import ai.koog.agents.core.tools.ToolRegistry")
            source.addImport("import ${spec.packageName}.tools.createToolRegistry")
        }

        source.addDeclaration(renderMainFunction(spec))

        return source.render()
    }

    private fun renderMainFunction(spec: ProjectSpec): String = buildString {
        val provider = spec.llmProvider

        appendLine("fun main() = runBlocking {")

        if (provider.requiresApiKey) {
            val envVar = requireNotNull(provider.envVarName)
            appendLine("    val apiKey: String = System.getenv(\"$envVar\")")
            appendLine("        ?: error(\"Environment variable $envVar is not set.\")")
        } else {
            appendLine("    val apiKey: String? = null")
            appendLine("    // Ollama does not require an API key.")
        }
        appendLine()

        if (spec.tooling.enabled) {
            if (provider.requiresApiKey) {
                appendLine("    val toolRegistry = createToolRegistry(requireNotNull(apiKey))")
            } else {
                appendLine("    val toolRegistry = createToolRegistry()")
            }
            appendLine()
        }

        provider.executorSetupLines.forEach { setupLine ->
            appendLine("    $setupLine")
        }
        if (provider.executorSetupLines.isNotEmpty()) {
            appendLine()
        }

        appendLine("    val strategy = functionalStrategy<String, String> { input ->")
        if (spec.tooling.enabled) {
            appendLine("        // requestLLM executes a single LLM request.")
            appendLine("        // If your tools require multi-turn execution, implement a loop using executeTool.")
        }
        appendLine("        val response = requestLLM(input)")
        appendLine("        response.asAssistantMessage().content")
        appendLine("    }")
        appendLine()

        appendLine("    val agent = AIAgent(")
        appendLine("        promptExecutor = ${provider.executorExpression},")
        appendLine("        llmModel = ${provider.modelExpression},")
        appendLine("        strategy = strategy,")
        if (spec.tooling.enabled) {
            appendLine("        toolRegistry = toolRegistry,")
        }
        appendLine("    )")
        appendLine()

        appendLine("    val result = agent.run(\"What is 12 × 9?\")")
        appendLine("    println(result)")
        append("}")
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
        if (spec.tooling.hasMcpTools) {
            out += GeneratedFile("$base/McpTools.kt", renderMcpToolsKt(spec))
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
        val suspendModifier = if (spec.tooling.hasMcpTools) "suspend " else ""
        appendLine("${suspendModifier}fun createToolRegistry(apiKey: String? = null): ToolRegistry {")
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
        if (spec.tooling.hasMcpTools) {
            appendLine("    registries += createMcpToolRegistry()")
        }
        appendLine("    return registries.fold(ToolRegistry { }) { acc, item -> acc + item }")
        appendLine("}")
    }

    private fun renderMcpToolsKt(spec: ProjectSpec): String = buildString {
        appendLine("package ${spec.packageName}.tools")
        appendLine()
        appendLine("import ai.koog.agents.mcp.McpToolRegistryProvider")
        appendLine("import ai.koog.agents.core.tools.ToolRegistry")
        appendLine()
        appendLine("suspend fun createMcpToolRegistry(): ToolRegistry {")
        val servers = spec.tooling.mcpServers.ifEmpty {
            listOf(com.example.kooggen.model.McpServerSpec("path/to/mcp/server"))
        }
        if (servers.size == 1) {
            appendLine("    val process = ProcessBuilder(\"${escapeKotlinString(servers[0].serverCommand)}\").start()")
            appendLine("    return McpToolRegistryProvider.fromProcess(process = process)")
        } else {
            servers.forEachIndexed { i, server ->
                appendLine("    val process${i + 1} = ProcessBuilder(\"${escapeKotlinString(server.serverCommand)}\").start()")
            }
            servers.forEachIndexed { i, _ ->
                appendLine("    val registry${i + 1} = McpToolRegistryProvider.fromProcess(process = process${i + 1})")
            }
            val combined = (1..servers.size).joinToString(" + ") { "registry$it" }
            appendLine("    return $combined")
        }
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
        appendLine("Minimal Koog functional-agent starter generated by `koog-starter-generator`.")
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
        appendLine("- `functionalStrategy<String, String>` with a single `requestLLM` call")
        appendLine("- `AIAgent` wired to the functional strategy")
        appendLine("- LLM provider: ${spec.llmProvider.displayName}")
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
                appendLine("- One file per nested agent tool under `tools/agents/`")
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
        .joinToString("") { part -> part.replaceFirstChar { ch -> ch.uppercase() } }
        .ifBlank { "Agent" }

    private fun toGeneratedAgentTypeName(value: String): String {
        val pascal = toPascalCase(value)
        return if (pascal.endsWith("Agent")) pascal else "${pascal}Agent"
    }
}
