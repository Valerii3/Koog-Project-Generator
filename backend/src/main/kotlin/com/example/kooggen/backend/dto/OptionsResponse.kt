package com.example.kooggen.backend.dto

import kotlinx.serialization.Serializable

@Serializable
data class OptionsResponse(
    val agentTypes: List<AgentTypeOption>,
    val providers: List<ProviderOption>,
    val tools: List<ToolOption>,
    val features: List<FeatureOption>
)

@Serializable
data class AgentTypeOption(
    val id: String,
    val label: String,
    val description: String
)

@Serializable
data class ProviderOption(
    val id: String,
    val label: String,
    val description: String,
    val envVar: String?
)

@Serializable
data class ToolOption(
    val id: String,
    val label: String,
    val description: String
)

@Serializable
data class FeatureOption(
    val id: String,
    val label: String,
    val description: String,
    val implemented: Boolean
)
