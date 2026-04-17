package com.example.kooggen.model

enum class ToolCapability(
    val cliLabel: String,
    val description: String
) {
    BUILT_IN(
        cliLabel = "Add built-in tools",
        description = "Adds SayToUser, AskUser, ExitTool, and file tools (read/list/write)."
    ),
    ANNOTATION_BASED(
        cliLabel = "Create annotation-based tools",
        description = "Scaffolds ToolSet methods with @Tool and @LLMDescription annotations."
    ),
    AGENT_AS_TOOL(
        cliLabel = "Create agents as tools",
        description = "Scaffolds specialized sub-agents via AIAgentService.createAgentTool()."
    )
}

enum class ToolParameterType(val kotlinType: String, val displayName: String) {
    STRING("String", "String"),
    INT("Int", "Int"),
    DOUBLE("Double", "Double"),
    BOOLEAN("Boolean", "Boolean")
}

data class ToolParameterSpec(
    val name: String,
    val type: ToolParameterType,
    val description: String
)

data class AnnotationToolSpec(
    val functionName: String,
    val customName: String?,
    val description: String,
    val parameters: List<ToolParameterSpec>
)

data class AgentAsToolSpec(
    val agentName: String,
    val agentDescription: String,
    val inputDescription: String,
    val systemPrompt: String
)

data class ProjectToolingSpec(
    val selectedCapabilities: Set<ToolCapability> = emptySet(),
    val annotationToolSetClassName: String = "UserToolSet",
    val annotationTools: List<AnnotationToolSpec> = emptyList(),
    val agentAsTools: List<AgentAsToolSpec> = emptyList()
) {
    val hasBuiltInTools: Boolean = ToolCapability.BUILT_IN in selectedCapabilities
    val hasAnnotationTools: Boolean = ToolCapability.ANNOTATION_BASED in selectedCapabilities
    val hasAgentAsTools: Boolean = ToolCapability.AGENT_AS_TOOL in selectedCapabilities
    val enabled: Boolean = selectedCapabilities.isNotEmpty()
}
