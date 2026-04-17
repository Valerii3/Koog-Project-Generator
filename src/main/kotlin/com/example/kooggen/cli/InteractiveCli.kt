package com.example.kooggen.cli

import com.example.kooggen.model.AgentTemplateType
import com.example.kooggen.model.AgentFeatureType
import com.example.kooggen.model.PlannerType
import com.example.kooggen.model.AgentPersistenceFeatureConfig
import com.example.kooggen.model.AgentAsToolSpec
import com.example.kooggen.model.AnnotationToolSpec
import com.example.kooggen.model.ChatMemoryFeatureConfig
import com.example.kooggen.model.EventHandlerFeatureConfig
import com.example.kooggen.model.ProjectFeatureSpec
import com.example.kooggen.model.ProjectSpec
import com.example.kooggen.model.ProjectToolingSpec
import com.example.kooggen.model.LlmProvider
import com.example.kooggen.model.ToolCapability
import com.example.kooggen.model.ToolParameterSpec
import com.example.kooggen.model.ToolParameterType
import com.example.kooggen.model.TracingFeatureConfig
import com.example.kooggen.validation.InputValidators
import java.nio.file.Path
import kotlin.io.path.absolute

class InteractiveCli {
    fun collectSpec(): ProjectSpec {
        println("Koog Starter Generator (v0)")
        println("This CLI creates a ZIP with a new Koog starter project.")
        println()

        val projectName = askProjectName()
        val outputDir = askOutputPath()
        val packageName = askPackageName(projectName)

        println()
        println("Stage 1/4: Agent Template")
        val templateType = askTemplateType()

        val plannerType = if (templateType == AgentTemplateType.PLANNER) {
            println()
            println("Stage 1b: Planner Type")
            askPlannerType()
        } else {
            null
        }

        println()
        println("Stage 2/4: LLM Provider")
        val llmProvider = askLlmProvider()

        val tooling: ProjectToolingSpec
        val features: ProjectFeatureSpec
        when (templateType) {
            AgentTemplateType.PLANNER -> {
                println()
                println("Stages 3/4 and 4/4 are skipped for Planner agents.")
                tooling = ProjectToolingSpec()
                features = ProjectFeatureSpec()
            }
            AgentTemplateType.FUNCTIONAL -> {
                println()
                println("Stage 3/4: Tools")
                tooling = askToolingSpec()
                println()
                println("Stage 4/4: Agent Features are not supported for Functional agents.")
                features = ProjectFeatureSpec()
            }
            else -> {
                println()
                println("Stage 3/4: Tools")
                tooling = askToolingSpec()
                println()
                println("Stage 4/4: Agent Features")
                features = askFeatureSpec()
            }
        }

        return ProjectSpec(
            projectName = projectName,
            outputDir = outputDir,
            packageName = packageName,
            templateType = templateType,
            llmProvider = llmProvider,
            tooling = tooling,
            features = features,
            plannerType = plannerType
        )
    }

    fun confirmOverwrite(path: Path): Boolean {
        while (true) {
            print("Archive ${path.toAbsolutePath()} already exists. Overwrite? [y/N]: ")
            val value = readlnOrNull()?.trim()?.lowercase().orEmpty()
            when (value) {
                "y", "yes" -> return true
                "", "n", "no" -> return false
            }
            println("Please answer with 'y' or 'n'.")
        }
    }

    private fun askProjectName(): String = askWithValidation(
        prompt = "Project name",
        defaultValue = "koog-basic-agent",
        validator = InputValidators::validateProjectName
    )

    private fun askLlmProvider(): LlmProvider {
        while (true) {
            println("Choose LLM provider:")
            LlmProvider.entries.forEachIndexed { index, provider ->
                val apiKeyNote = provider.envVarName?.let { "env: $it" } ?: "no API key"
                println("  ${index + 1}. ${provider.displayName} ($apiKeyNote)")
                println("     ${provider.description}")
            }

            print("Select [1-${LlmProvider.entries.size}] (default 1): ")
            val raw = readlnOrNull()?.trim().orEmpty()
            val selectedIndex = if (raw.isBlank()) 1 else raw.toIntOrNull()
            if (selectedIndex == null || selectedIndex !in 1..LlmProvider.entries.size) {
                println("Please select a valid provider option.")
                continue
            }
            return LlmProvider.entries[selectedIndex - 1]
        }
    }

    private fun askOutputPath(): Path {
        val defaultOutput = Path.of(".").absolute().normalize().toString()
        while (true) {
            print("Output directory [$defaultOutput]: ")
            val raw = readlnOrNull()?.trim().orEmpty()
            val selected = if (raw.isBlank()) defaultOutput else raw
            val path = Path.of(selected)
            val error = InputValidators.validateOutputPath(path)
            if (error == null) {
                return path.toAbsolutePath().normalize()
            }
            println("Invalid output path: $error")
        }
    }

    private fun askPackageName(projectName: String): String {
        val sanitized = projectName.lowercase().replace('-', '.').replace('_', '.')
            .replace(Regex("[^a-z0-9.]"), "")
            .trim('.')
            .ifBlank { "app" }
        val defaultPackage = "com.example.$sanitized"
        return askWithValidation(
            prompt = "Package name",
            defaultValue = defaultPackage,
            validator = InputValidators::validatePackageName
        )
    }

    private fun askTemplateType(): AgentTemplateType {
        while (true) {
            println("Choose agent template type:")
            AgentTemplateType.entries.forEachIndexed { index, type ->
                val status = if (type.implemented) "implemented" else "not implemented yet"
                println("  ${index + 1}. ${type.cliLabel} ($status)")
                println("     ${type.description}")
            }

            print("Select [1-${AgentTemplateType.entries.size}] (default 1): ")
            val raw = readlnOrNull()?.trim().orEmpty()
            val selectedIndex = if (raw.isBlank()) 1 else raw.toIntOrNull()
            if (selectedIndex == null || selectedIndex !in 1..AgentTemplateType.entries.size) {
                println("Please select a valid menu option.")
                continue
            }

            val selected = AgentTemplateType.entries[selectedIndex - 1]
            if (!selected.implemented) {
                println("${selected.cliLabel} is planned but not implemented in v0. Please pick Basic agent.")
                continue
            }
            return selected
        }
    }

    private fun askPlannerType(): PlannerType {
        while (true) {
            println("Choose LLM-based planner:")
            PlannerType.entries.forEachIndexed { index, plannerType ->
                println("  ${index + 1}. ${plannerType.cliLabel}")
                println("     ${plannerType.description}")
            }

            print("Select [1-${PlannerType.entries.size}] (default 1): ")
            val raw = readlnOrNull()?.trim().orEmpty()
            val selectedIndex = if (raw.isBlank()) 1 else raw.toIntOrNull()
            if (selectedIndex == null || selectedIndex !in 1..PlannerType.entries.size) {
                println("Please select a valid planner option.")
                continue
            }
            return PlannerType.entries[selectedIndex - 1]
        }
    }

    private fun askToolingSpec(): ProjectToolingSpec {
        println("Select tool options (multi-select):")
        ToolCapability.entries.forEachIndexed { index, option ->
            println("  ${index + 1}. ${option.cliLabel}")
            println("     ${option.description}")
        }
        println("Press Enter to skip tools for now.")

        val selected = askMultiChoice(
            prompt = "Tool options (comma-separated numbers)",
            entriesCount = ToolCapability.entries.size,
            defaultValue = ""
        ).map { ToolCapability.entries[it - 1] }.toMutableSet()

        if (selected.isEmpty()) {
            return ProjectToolingSpec()
        }

        var toolSetClassName = "UserToolSet"
        var annotationTools = emptyList<AnnotationToolSpec>()
        val agentAsTools = mutableListOf<AgentAsToolSpec>()

        if (ToolCapability.ANNOTATION_BASED in selected) {
            toolSetClassName = askWithValidation(
                prompt = "Annotation tool set class name",
                defaultValue = "UserToolSet",
                validator = InputValidators::validateClassName
            )

            val toolCount = askInt(
                prompt = "How many annotation-based tools to scaffold",
                defaultValue = 1,
                min = 1,
                max = 20
            )

            annotationTools = (1..toolCount).map { index ->
                askAnnotationTool(index)
            }
        }

        if (ToolCapability.AGENT_AS_TOOL in selected) {
            val usedNames = mutableSetOf<String>()
            val usedTypeNames = mutableSetOf<String>()
            while (true) {
                val shouldAdd = askYesNo(
                    prompt = if (agentAsTools.isEmpty()) {
                        "Add an agent-as-tool definition"
                    } else {
                        "Add another agent-as-tool definition"
                    },
                    defaultYes = agentAsTools.isEmpty()
                )
                if (!shouldAdd) {
                    break
                }
                val spec = askAgentAsTool(agentAsTools.size + 1)
                if (spec.agentName in usedNames) {
                    println("Agent tool name '${spec.agentName}' already exists. Please choose a unique name.")
                    continue
                }
                val typeName = toGeneratedAgentTypeName(spec.agentName)
                if (typeName in usedTypeNames) {
                    println(
                        "Agent tool name '${spec.agentName}' collides with an existing generated file/class name ($typeName). " +
                            "Please choose a different name."
                    )
                    continue
                }
                usedNames += spec.agentName
                usedTypeNames += typeName
                agentAsTools += spec
            }
            if (agentAsTools.isEmpty()) {
                selected -= ToolCapability.AGENT_AS_TOOL
            }
        }

        return ProjectToolingSpec(
            selectedCapabilities = selected.toSet(),
            annotationToolSetClassName = toolSetClassName,
            annotationTools = annotationTools,
            agentAsTools = agentAsTools
        )
    }

    private fun askAgentAsTool(index: Int): AgentAsToolSpec {
        println()
        println("Configure agent-as-tool #$index")

        val agentName = askWithValidation(
            prompt = "Agent tool name (used as function/tool id)",
            defaultValue = "specialistAgent$index",
            validator = InputValidators::validateFunctionName
        )

        val agentDescription = askWithValidation(
            prompt = "Agent tool description",
            defaultValue = "Executes specialized task '$agentName'",
            validator = InputValidators::validateRequiredDescription
        )

        val inputDescription = askWithValidation(
            prompt = "Agent tool input description",
            defaultValue = "Input request for $agentName",
            validator = InputValidators::validateRequiredDescription
        )

        val systemPrompt = askWithValidation(
            prompt = "System prompt for nested agent",
            defaultValue = "You are a specialized assistant for '$agentName'.",
            validator = InputValidators::validateRequiredDescription
        )

        return AgentAsToolSpec(
            agentName = agentName,
            agentDescription = agentDescription,
            inputDescription = inputDescription,
            systemPrompt = systemPrompt
        )
    }

    private fun askFeatureSpec(): ProjectFeatureSpec {
        println("Add features one by one. Type a number to add/reconfigure, or 0 to continue.")
        val selected = linkedSetOf<AgentFeatureType>()
        var eventHandlerConfig = EventHandlerFeatureConfig()
        var chatMemoryConfig = ChatMemoryFeatureConfig()
        var agentPersistenceConfig = AgentPersistenceFeatureConfig()
        var tracingConfig = TracingFeatureConfig()

        while (true) {
            println()
            println("Feature menu:")
            AgentFeatureType.entries.forEachIndexed { index, feature ->
                val status = when {
                    !feature.implemented -> "not implemented yet"
                    feature in selected -> "selected"
                    else -> "available"
                }
                println("  ${index + 1}. ${feature.cliLabel} ($status)")
                println("     ${feature.description}")
            }
            println("  0. Continue")

            val option = askInt(
                prompt = "Choose feature",
                defaultValue = 0,
                min = 0,
                max = AgentFeatureType.entries.size
            )

            if (option == 0) {
                break
            }

            val selectedFeature = AgentFeatureType.entries[option - 1]
            if (!selectedFeature.implemented) {
                println("${selectedFeature.cliLabel} is planned but not implemented yet.")
                continue
            }

            when (selectedFeature) {
                AgentFeatureType.EVENT_HANDLER -> {
                    val enable = askYesNo(
                        prompt = "Enable event handling callbacks in generated agent",
                        defaultYes = true
                    )
                    if (!enable) {
                        selected -= selectedFeature
                        eventHandlerConfig = EventHandlerFeatureConfig(enabled = false)
                        println("Event handler feature removed.")
                        continue
                    }

                    val includeToolCall = askYesNo(
                        prompt = "Include onToolCallStarting callback",
                        defaultYes = true
                    )
                    val includeAgentCompleted = askYesNo(
                        prompt = "Include onAgentCompleted callback",
                        defaultYes = true
                    )

                    eventHandlerConfig = EventHandlerFeatureConfig(
                        enabled = true,
                        includeOnToolCallStarting = includeToolCall,
                        includeOnAgentCompleted = includeAgentCompleted
                    )
                    selected += selectedFeature
                    println("Event handler feature added.")
                }
                AgentFeatureType.CHAT_MEMORY -> {
                    val enable = askYesNo(
                        prompt = "Enable in-memory ChatMemory in generated agent",
                        defaultYes = true
                    )
                    if (!enable) {
                        selected -= selectedFeature
                        chatMemoryConfig = ChatMemoryFeatureConfig(enabled = false)
                        println("Chat memory feature removed.")
                        continue
                    }

                    val windowSize = askInt(
                        prompt = "Chat memory window size",
                        defaultValue = 20,
                        min = 1,
                        max = 2000
                    )
                    val sessionId = askWithValidation(
                        prompt = "Default session ID used in sample run",
                        defaultValue = "session-1",
                        validator = InputValidators::validateSessionId
                    )

                    chatMemoryConfig = ChatMemoryFeatureConfig(
                        enabled = true,
                        windowSize = windowSize,
                        sessionId = sessionId
                    )
                    selected += selectedFeature
                    println("Chat memory feature added.")
                }
                AgentFeatureType.AGENT_PERSISTENCE -> {
                    val enable = askYesNo(
                        prompt = "Enable Agent Persistence in generated agent",
                        defaultYes = true
                    )
                    if (!enable) {
                        selected -= selectedFeature
                        agentPersistenceConfig = AgentPersistenceFeatureConfig(enabled = false)
                        println("Agent persistence feature removed.")
                        continue
                    }

                    agentPersistenceConfig = AgentPersistenceFeatureConfig(
                        enabled = true,
                        enableAutomaticPersistence = false
                    )
                    selected += selectedFeature
                    println("Agent persistence feature added (InMemoryPersistenceStorageProvider + enableAutomaticPersistence = false).")
                }
                AgentFeatureType.TRACING -> {
                    val enable = askYesNo(
                        prompt = "Enable tracing logs in generated agent",
                        defaultYes = true
                    )
                    if (!enable) {
                        selected -= selectedFeature
                        tracingConfig = TracingFeatureConfig(enabled = false)
                        println("Tracing feature removed.")
                        continue
                    }

                    tracingConfig = TracingFeatureConfig(enabled = true)
                    selected += selectedFeature
                    println("Tracing feature added (TraceFeatureMessageLogWriter).")
                }
            }
        }

        return ProjectFeatureSpec(
            selected = selected.toSet(),
            eventHandler = eventHandlerConfig,
            chatMemory = chatMemoryConfig,
            agentPersistence = agentPersistenceConfig,
            tracing = tracingConfig
        )
    }

    private fun askAnnotationTool(index: Int): AnnotationToolSpec {
        println()
        println("Configure annotation-based tool #$index")

        val functionName = askWithValidation(
            prompt = "Function name",
            defaultValue = "tool$index",
            validator = InputValidators::validateFunctionName
        )

        val description = askWithValidation(
            prompt = "Tool description",
            defaultValue = "TODO: Describe what $functionName should do",
            validator = InputValidators::validateRequiredDescription
        )

        print("Custom tool name for LLM (optional, press Enter to use function name): ")
        val customName = readlnOrNull()?.trim().orEmpty().ifBlank { null }

        val parameterCount = askInt(
            prompt = "Parameter count for $functionName",
            defaultValue = 0,
            min = 0,
            max = 10
        )

        val parameters = (1..parameterCount).map { paramIndex ->
            askToolParameter(functionName, paramIndex)
        }

        return AnnotationToolSpec(
            functionName = functionName,
            customName = customName,
            description = description,
            parameters = parameters
        )
    }

    private fun askToolParameter(functionName: String, index: Int): ToolParameterSpec {
        println("Parameter #$index for $functionName")

        val name = askWithValidation(
            prompt = "Parameter name",
            defaultValue = "param$index",
            validator = InputValidators::validateParameterName
        )

        println("Parameter type:")
        ToolParameterType.entries.forEachIndexed { typeIndex, type ->
            println("  ${typeIndex + 1}. ${type.displayName}")
        }

        val typeIndex = askInt(
            prompt = "Choose type",
            defaultValue = 1,
            min = 1,
            max = ToolParameterType.entries.size
        )

        val description = askWithValidation(
            prompt = "Parameter description",
            defaultValue = "TODO: describe parameter '$name'",
            validator = InputValidators::validateRequiredDescription
        )

        return ToolParameterSpec(
            name = name,
            type = ToolParameterType.entries[typeIndex - 1],
            description = description
        )
    }

    private fun askMultiChoice(prompt: String, entriesCount: Int, defaultValue: String): List<Int> {
        while (true) {
            val suffix = if (defaultValue.isBlank()) "" else " [$defaultValue]"
            print("$prompt$suffix: ")
            val rawInput = readlnOrNull()?.trim().orEmpty()
            val value = if (rawInput.isBlank()) defaultValue else rawInput

            if (value.isBlank()) {
                return emptyList()
            }

            val parsed = value.split(',').map { it.trim() }.filter { it.isNotBlank() }
            val indices = parsed.map { it.toIntOrNull() }

            if (indices.any { it == null || it !in 1..entriesCount }) {
                println("Please provide comma-separated numbers between 1 and $entriesCount.")
                continue
            }

            return indices.filterNotNull().distinct()
        }
    }

    private fun askInt(prompt: String, defaultValue: Int, min: Int, max: Int): Int {
        while (true) {
            print("$prompt [$defaultValue]: ")
            val raw = readlnOrNull()?.trim().orEmpty()
            val value = if (raw.isBlank()) defaultValue else raw.toIntOrNull()
            if (value == null || value !in min..max) {
                println("Please enter a number between $min and $max.")
                continue
            }
            return value
        }
    }

    private fun askYesNo(prompt: String, defaultYes: Boolean): Boolean {
        val defaultLabel = if (defaultYes) "[Y/n]" else "[y/N]"
        while (true) {
            print("$prompt $defaultLabel: ")
            val raw = readlnOrNull()?.trim()?.lowercase().orEmpty()
            if (raw.isBlank()) {
                return defaultYes
            }
            when (raw) {
                "y", "yes" -> return true
                "n", "no" -> return false
            }
            println("Please answer with 'y' or 'n'.")
        }
    }

    private fun askWithValidation(
        prompt: String,
        defaultValue: String,
        validator: (String) -> String?
    ): String {
        while (true) {
            print("$prompt [$defaultValue]: ")
            val raw = readlnOrNull()?.trim().orEmpty()
            val value = if (raw.isBlank()) defaultValue else raw
            val error = validator(value)
            if (error == null) {
                return value
            }
            println("Invalid $prompt: $error")
        }
    }

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
