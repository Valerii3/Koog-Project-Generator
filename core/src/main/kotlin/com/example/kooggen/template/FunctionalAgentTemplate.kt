package com.example.kooggen.template

import com.example.kooggen.model.AgentTemplateType
import com.example.kooggen.model.AgentAsToolSpec
import com.example.kooggen.model.AnnotationToolSpec
import com.example.kooggen.model.LlmProvider
import com.example.kooggen.model.ProjectSpec
import com.example.kooggen.model.ProjectToolingSpec
import com.example.kooggen.model.ToolParameterSpec
import com.example.kooggen.model.ToolParameterType

class FunctionalAgentTemplate : ProjectTemplate {
    override val type: AgentTemplateType = AgentTemplateType.FUNCTIONAL

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
        source.addImport("import ai.koog.agents.core.dsl.builder.forwardTo")
        source.addImport("import ai.koog.agents.core.dsl.builder.strategy")
        source.addImport("import ai.koog.agents.core.dsl.extension.nodeLLMRequest")
        source.addImport("import ai.koog.agents.core.dsl.extension.onAssistantMessage")
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

        source.addDeclaration(renderTopLevelAgentStrategy())

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

    private fun renderTopLevelApiKey(provider: LlmProvider): String {
        val envVar = requireNotNull(provider.envVarName)
        return """
            private val apiKey: String = System.getenv("$envVar")
                ?: error("Environment variable $envVar is not set.")
        """.trimIndent()
    }

    private fun renderTopLevelAgentStrategy(): String = """
        val agentStrategy = strategy<String, String>("hello strategy") {
            val nodeSendInput by nodeLLMRequest()

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
        }
    """.trimIndent()

    private fun renderMainFunction(spec: ProjectSpec): String = buildString {
        val provider = spec.llmProvider

        appendLine("fun main() = runBlocking {")

        if (spec.tooling.enabled) {
            append(renderToolRegistryBlock(spec))
            appendLine()
        }

        appendLine("    val agent = AIAgent(")
        appendLine("        promptExecutor = ${provider.executorExpression},")
        appendLine("        llmModel = ${provider.modelExpression},")
        appendLine("        strategy = agentStrategy,")
        if (spec.tooling.enabled) {
            appendLine("        toolRegistry = toolRegistry,")
        }
        appendLine("    )")
        appendLine()

        appendLine("    val result = agent.run(\"What is 12 × 9?\")")
        appendLine("    println(result)")
        append("}")
    }

    private fun renderToolRegistryBlock(spec: ProjectSpec): String = buildString {
        val parts = mutableListOf<String>()
        if (spec.tooling.hasBuiltInTools) {
            parts += "createBuiltInToolRegistry()"
        }

        val dslLines = mutableListOf<String>()
        if (spec.tooling.hasAnnotationTools) {
            dslLines += "tools(${spec.tooling.annotationToolSetClassName}().asTools())"
        }
        if (spec.tooling.hasAgentAsTools) {
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
            appendLine("    val toolRegistry = ${parts.joinToString(" + ")}")
        }
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
                ToolParameterSpec(
                    name = "input",
                    type = ToolParameterType.STRING,
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
        appendLine("Minimal Koog functional-agent starter generated by `koog-starter-generator`.")
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
        appendLine("- `agentStrategy` declared at top level using the `strategy` DSL")
        appendLine("- `AIAgent` wired to `agentStrategy`")
        appendLine("- LLM provider: ${spec.llmProvider.displayName}")
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
        .joinToString("") { part -> part.replaceFirstChar { ch -> ch.uppercase() } }
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
