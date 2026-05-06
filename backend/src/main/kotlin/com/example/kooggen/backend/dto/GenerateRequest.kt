package com.example.kooggen.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class GenerateRequest(
    val artifact: String = "com.example.hello",
    val agentType: String = "BASIC",
    val provider: String = "OPENAI",
    val tools: List<String> = emptyList(),
    val features: List<String> = emptyList(),
    /** `zip` — full project archive. `agent_kotlin` — single `Main.kt` source as `Agent.kt` (no Gradle). */
    val outputFormat: String = "zip"
)
