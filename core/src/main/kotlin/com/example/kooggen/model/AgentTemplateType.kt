package com.example.kooggen.model

enum class AgentTemplateType(
    val cliLabel: String,
    val description: String,
    val implemented: Boolean,
    val archiveSuffix: String
) {
    BASIC(
        cliLabel = "Basic agent",
        description = "Predefined strategy for common use cases; fastest path to first runnable Koog agent.",
        implemented = true,
        archiveSuffix = "basic-agent"
    ),
    FUNCTIONAL(
        cliLabel = "Functional agent",
        description = "Custom strategy via functionalStrategy DSL; full control over the LLM request/response flow.",
        implemented = true,
        archiveSuffix = "functional-agent"
    ),
    GRAPH(
        cliLabel = "Graph-based agent",
        description = "Custom graph strategy with explicit node transitions; visualizable state machine with nodes and edges.",
        implemented = true,
        archiveSuffix = "graph-agent"
    ),
    PLANNER(
        cliLabel = "Planner agent",
        description = "LLM-based planner agent for multi-step reasoning and execution on string-based state.",
        implemented = true,
        archiveSuffix = "planner-agent"
    )
}
