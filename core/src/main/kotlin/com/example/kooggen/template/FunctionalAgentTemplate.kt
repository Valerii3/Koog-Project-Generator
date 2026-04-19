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
        source.addImport("import ai.koog.agents.core.dsl.builder.functionalStrategy")
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
            if (spec.tooling.hasMcpTools) {
                source.addImport("import ai.koog.agents.mcp.McpToolRegistryProvider")
            }
        }

        if (provider.requiresApiKey) {
            source.addDeclaration(renderTopLevelApiKey(provider))
        }
        provider.executorSetupLines.forEach { setupLine ->
            source.addDeclaration("private $setupLine")
        }

        source.addDeclaration(renderTopLevelAgentStrategy())

        if (spec.tooling.hasAnnotationTools) {
            source.addDeclaration(renderSampleToolSetClass(spec.tooling))
        }
        if (spec.tooling.hasBuiltInTools) {
            source.addDeclaration(renderBuiltInToolRegistryFunction())
        }
        if (spec.tooling.hasAgentAsTools) {
            effectiveAgentAsTools(spec).forEach { agentTool ->
                source.addDeclaration(renderAgentAsToolVals(spec, agentTool))
            }
        }
        if (spec.tooling.hasAnnotationTools || spec.tooling.hasAgentAsTools) {
            source.addDeclaration(renderUserToolRegistryFunction(spec))
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
        val strategy = functionalStrategy<String, String> { input ->
            val response = requestLLM(input)
            response.asAssistantMessage().content
        }
    """.trimIndent()

    private fun renderMainFunction(spec: ProjectSpec): String = buildString {
        val provider = spec.llmProvider

        appendLine("fun main() = runBlocking {")

        if (spec.tooling.hasMcpTools) {
            append(renderMcpMainBlock(spec))
            appendLine()
        }
        if (spec.tooling.enabled) {
            append(renderToolRegistryBlock(spec))
            appendLine()
        }

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

    private fun renderToolRegistryBlock(spec: ProjectSpec): String = buildString {
        val parts = mutableListOf<String>()
        if (spec.tooling.hasBuiltInTools) parts += "createBuiltInToolRegistry()"
        if (spec.tooling.hasAnnotationTools || spec.tooling.hasAgentAsTools) parts += "createUserToolRegistry()"
        if (spec.tooling.hasMcpTools) parts += "mcpToolRegistry"

        if (parts.isEmpty()) {
            append("    val toolRegistry = ToolRegistry { }")
        } else {
            append("    val toolRegistry = ${parts.joinToString(" + ")}")
        }
        appendLine()
    }

    private fun renderMcpMainBlock(spec: ProjectSpec): String = buildString {
        val servers = spec.tooling.mcpServers.ifEmpty {
            listOf(com.example.kooggen.model.McpServerSpec("java -jar mcp-server.jar"))
        }
        if (servers.size == 1) {
            val args = servers[0].serverCommand.split(" ")
                .filter { it.isNotBlank() }
                .joinToString(", ") { "\"${escapeKotlinString(it)}\"" }
            appendLine("    val process = ProcessBuilder($args).start()")
            appendLine("    val mcpToolRegistry = McpToolRegistryProvider.fromProcess(process = process)")
        } else {
            servers.forEachIndexed { i, server ->
                val args = server.serverCommand.split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(", ") { "\"${escapeKotlinString(it)}\"" }
                appendLine("    val process${i + 1} = ProcessBuilder($args).start()")
            }
            servers.forEachIndexed { i, _ ->
                appendLine("    val mcpRegistry${i + 1} = McpToolRegistryProvider.fromProcess(process = process${i + 1})")
            }
            val combined = (1..servers.size).joinToString(" + ") { "mcpRegistry$it" }
            appendLine("    val mcpToolRegistry = $combined")
        }
    }

    private fun effectiveAgentAsTools(spec: ProjectSpec): List<AgentAsToolSpec> =
        spec.tooling.agentAsTools.ifEmpty {
            listOf(
                AgentAsToolSpec(
                    agentName = "",
                    agentDescription = "",
                    inputDescription = "",
                    systemPrompt = ""
                )
            )
        }

    private fun renderUserToolRegistryFunction(spec: ProjectSpec): String = buildString {
        appendLine("fun createUserToolRegistry(): ToolRegistry = ToolRegistry {")
        if (spec.tooling.hasAnnotationTools) {
            appendLine("    tools(${spec.tooling.annotationToolSetClassName}().asTools())")
        }
        if (spec.tooling.hasAgentAsTools) {
            effectiveAgentAsTools(spec).forEach { agentTool ->
                appendLine("    tool(${toAgentToolValName(agentTool.agentName)})")
            }
        }
        append("}")
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
        val agentNameLiteral = tool.agentName.ifBlank { "userAgent" }
        val agentDescription = tool.agentDescription.ifBlank { "TODO: describe what this agent does" }
        val inputDescription = tool.inputDescription.ifBlank { "TODO: describe the expected input" }

        appendLine("val $serviceVar = AIAgentService(")
        appendLine("    promptExecutor = ${provider.executorExpression},")
        appendLine("    llmModel = ${provider.modelExpression},")
        appendLine("    systemPrompt = \"${escapeKotlinString(systemPrompt)}\",")
        appendLine(")")
        appendLine()
        appendLine("val $toolVar = $serviceVar.createAgentTool(")
        appendLine("    agentName = \"${escapeKotlinString(agentNameLiteral)}\",")
        appendLine("    agentDescription = \"${escapeKotlinString(agentDescription)}\",")
        appendLine("    inputDescription = \"${escapeKotlinString(inputDescription)}\",")
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
        val camel = toCamelCase(agentName.ifBlank { "user" })
        val base = if (camel.endsWith("Agent")) camel else "${camel}Agent"
        return "${base}Service"
    }

    private fun toAgentToolValName(agentName: String): String {
        val camel = toCamelCase(agentName.ifBlank { "user" })
        val base = if (camel.endsWith("Agent")) camel else "${camel}Agent"
        return "${base}Tool"
    }
}
