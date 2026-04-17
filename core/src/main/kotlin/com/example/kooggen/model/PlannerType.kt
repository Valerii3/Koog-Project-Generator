package com.example.kooggen.model

enum class PlannerType(
    val cliLabel: String,
    val description: String,
    val className: String
) {
    SIMPLE_LLM(
        cliLabel = "SimpleLLMPlanner",
        description = "Generates a plan once at the start and follows it until completion. Override assessPlan to add custom replanning logic.",
        className = "SimpleLLMPlanner"
    ),
    SIMPLE_LLM_WITH_CRITIC(
        cliLabel = "SimpleLLMWithCriticPlanner",
        description = "Extends SimpleLLMPlanner with an LLM-based critic that evaluates the plan and decides whether the agent should replan.",
        className = "SimpleLLMWithCriticPlanner"
    )
}
