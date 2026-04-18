package com.example.kooggen.backend.routes

import com.example.kooggen.backend.dto.AgentTypeOption
import com.example.kooggen.backend.dto.FeatureOption
import com.example.kooggen.backend.dto.OptionsResponse
import com.example.kooggen.backend.dto.ProviderOption
import com.example.kooggen.backend.dto.ToolOption
import com.example.kooggen.model.AgentFeatureType
import com.example.kooggen.model.LlmProvider
import com.example.kooggen.model.ToolCapability
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.optionsRoute() {
    get("/options") {
        val response = OptionsResponse(
            agentTypes = listOf(
                AgentTypeOption("BASIC", "Basic Agent", "Predefined strategy for common use cases."),
                AgentTypeOption("FUNCTIONAL", "Functional Agent", "Custom strategy via functionalStrategy DSL."),
                AgentTypeOption("GRAPH", "Graph-based Agent", "Explicit state machine with nodes and edges."),
                AgentTypeOption("PLANNER_SIMPLE", "SimpleLLMPlanner", "Generates a plan once and follows it."),
                AgentTypeOption("PLANNER_CRITIC", "SimpleLLMPlanner with Critics", "Extends SimpleLLMPlanner with an LLM-based critic.")
            ),
            providers = LlmProvider.entries.map { p ->
                ProviderOption(p.name, p.displayName, p.description, p.envVarName)
            },
            tools = ToolCapability.entries.map { t ->
                ToolOption(t.name, t.cliLabel, t.description)
            },
            features = AgentFeatureType.entries.map { f ->
                FeatureOption(f.name, f.cliLabel, f.description, f.implemented)
            }
        )
        call.respond(response)
    }
}
