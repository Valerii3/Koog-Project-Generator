package com.example.kooggen.template

import com.example.kooggen.model.AgentTemplateType
import com.example.kooggen.model.PlannerType
import com.example.kooggen.model.ProjectSpec

class PlannerAgentTemplate : ProjectTemplate {
    override val type: AgentTemplateType = AgentTemplateType.PLANNER

    override fun render(spec: ProjectSpec): List<GeneratedFile> {
        val base = spec.projectName
        return listOf(
            GeneratedFile("$base/settings.gradle.kts", renderSettingsGradle(spec)),
            GeneratedFile("$base/build.gradle.kts", renderBuildGradle(spec)),
            GeneratedFile("$base/gradle.properties", "kotlin.code.style=official\n"),
            GeneratedFile("$base/.env.example", renderEnvExample(spec)),
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
        val plannerType = requireNotNull(spec.plannerType) { "plannerType must be set for PLANNER template" }
        val source = KotlinSourceFile(spec.packageName)

        source.addImport("import ai.koog.agents.core.agent.config.AIAgentConfig")
        source.addImport("import ai.koog.agents.ext.agent.PlannerAIAgent")
        source.addImport("import ai.koog.agents.ext.strategy.AIAgentPlannerStrategy")
        when (plannerType) {
            PlannerType.SIMPLE_LLM ->
                source.addImport("import ai.koog.agents.ext.planner.SimpleLLMPlanner")
            PlannerType.SIMPLE_LLM_WITH_CRITIC ->
                source.addImport("import ai.koog.agents.ext.planner.SimpleLLMWithCriticPlanner")
        }
        source.addImport("import ai.koog.prompt.dsl.prompt")
        source.addImports(provider.importLines)
        source.addImport("import kotlinx.coroutines.runBlocking")

        source.addDeclaration(renderMainFunction(spec))

        return source.render()
    }

    private fun renderMainFunction(spec: ProjectSpec): String = buildString {
        val provider = spec.llmProvider
        val plannerType = requireNotNull(spec.plannerType)

        appendLine("fun main() = runBlocking {")

        if (provider.requiresApiKey) {
            val envVar = requireNotNull(provider.envVarName)
            appendLine("    val apiKey: String = System.getenv(\"$envVar\")")
            appendLine("        ?: error(\"Environment variable $envVar is not set.\")")
        } else {
            appendLine("    // Ollama does not require an API key.")
        }
        appendLine()

        provider.executorSetupLines.forEach { setupLine ->
            appendLine("    $setupLine")
        }
        if (provider.executorSetupLines.isNotEmpty()) {
            appendLine()
        }

        appendLine("    val executor = ${provider.executorExpression}")
        appendLine()

        when (plannerType) {
            PlannerType.SIMPLE_LLM -> {
                appendLine("    val planner = SimpleLLMPlanner()")
            }
            PlannerType.SIMPLE_LLM_WITH_CRITIC -> {
                appendLine("    val planner = SimpleLLMWithCriticPlanner()")
            }
        }
        appendLine()

        appendLine("    val strategy = AIAgentPlannerStrategy(")
        appendLine("        name = \"planner\",")
        appendLine("        planner = planner")
        appendLine("    )")
        appendLine()

        appendLine("    val agentConfig = AIAgentConfig(")
        appendLine("        prompt = prompt(\"planner\") {")
        appendLine("            system(\"You are a helpful planning assistant.\")")
        appendLine("        },")
        appendLine("        model = ${provider.modelExpression},")
        appendLine("        maxAgentIterations = 50")
        appendLine("    )")
        appendLine()

        appendLine("    val agent = PlannerAIAgent(")
        appendLine("        promptExecutor = executor,")
        appendLine("        strategy = strategy,")
        appendLine("        agentConfig = agentConfig")
        appendLine("    )")
        appendLine()

        appendLine("    val result = agent.run(\"Create a plan to organize a team meeting\")")
        appendLine("    println(result)")
        append("}")
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
        val plannerType = requireNotNull(spec.plannerType)
        appendLine("# ${spec.projectName}")
        appendLine()
        appendLine("Minimal Koog planner-agent starter generated by `koog-starter-generator`.")
        appendLine()
        appendLine("## Planner type: ${plannerType.cliLabel}")
        appendLine()
        appendLine(plannerType.description)
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
        appendLine("- `PlannerAIAgent` with `AIAgentPlannerStrategy`")
        appendLine("- Planner: `${plannerType.className}`")
        appendLine("- `AIAgentConfig` with `prompt` DSL and system prompt")
        appendLine("- LLM provider: ${spec.llmProvider.displayName}")
        appendLine("- String-based state: agent accepts a task string and returns a result string")
    }

    private fun renderGitIgnore(): String = """
        .gradle/
        build/
        .idea/
        *.iml
        .env
    """.trimIndent() + "\n"
}
