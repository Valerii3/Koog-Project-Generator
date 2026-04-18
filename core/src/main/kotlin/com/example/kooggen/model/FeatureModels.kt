package com.example.kooggen.model

enum class AgentFeatureType(
    val cliLabel: String,
    val description: String,
    val implemented: Boolean = true
) {
    EVENT_HANDLER(
        cliLabel = "Event handler",
        description = "Adds handleEvents callbacks (tool-call logging, completion events).",
        implemented = true
    ),
    CHAT_MEMORY(
        cliLabel = "Chat memory",
        description = "Adds in-memory ChatMemory with window size and session-based agent.run(..., sessionId).",
        implemented = true
    ),
    AGENT_PERSISTENCE(
        cliLabel = "Agent persistence",
        description = "Adds Persistence with InMemoryPersistenceStorageProvider and enableAutomaticPersistence = false.",
        implemented = true
    ),
    TRACING(
        cliLabel = "Tracing",
        description = "Adds Tracing feature with TraceFeatureMessageLogWriter(logger).",
        implemented = true
    ),
    LONG_TERM_MEMORY(
        cliLabel = "Long-term memory (Experimental)",
        description = "Adds LongTermMemory feature with InMemoryRecordStorage, SimilaritySearchStrategy (RAG retrieval) and optional automatic ingestion.",
        implemented = true
    ),
    OPEN_TELEMETRY_DATADOG(
        cliLabel = "OpenTelemetry — Datadog exporter",
        description = "Exports agent traces to Datadog LLM Observability via OpenTelemetry.",
        implemented = true
    ),
    OPEN_TELEMETRY_LANGFUSE(
        cliLabel = "OpenTelemetry — Langfuse exporter",
        description = "Exports agent traces to Langfuse for prompt and LLM observability via OpenTelemetry.",
        implemented = true
    ),
    OPEN_TELEMETRY_WEAVE(
        cliLabel = "OpenTelemetry — W&B Weave exporter",
        description = "Exports agent traces to Weights & Biases Weave for AI observability via OpenTelemetry.",
        implemented = true
    )
}

data class EventHandlerFeatureConfig(
    val enabled: Boolean = false,
    val includeOnToolCallStarting: Boolean = true,
    val includeOnAgentCompleted: Boolean = true
)

data class ChatMemoryFeatureConfig(
    val enabled: Boolean = false,
    val windowSize: Int = 20,
    val sessionId: String = "session-1"
)

data class AgentPersistenceFeatureConfig(
    val enabled: Boolean = false,
    val enableAutomaticPersistence: Boolean = false
)

data class TracingFeatureConfig(
    val enabled: Boolean = false
)

data class LongTermMemoryFeatureConfig(
    val enabled: Boolean = false,
    val topK: Int = 5,
    val enableAutomaticIngestion: Boolean = false
)

data class ProjectFeatureSpec(
    val selected: Set<AgentFeatureType> = emptySet(),
    val eventHandler: EventHandlerFeatureConfig = EventHandlerFeatureConfig(),
    val chatMemory: ChatMemoryFeatureConfig = ChatMemoryFeatureConfig(),
    val agentPersistence: AgentPersistenceFeatureConfig = AgentPersistenceFeatureConfig(),
    val tracing: TracingFeatureConfig = TracingFeatureConfig(),
    val longTermMemory: LongTermMemoryFeatureConfig = LongTermMemoryFeatureConfig()
) {
    val hasEventHandler: Boolean = AgentFeatureType.EVENT_HANDLER in selected && eventHandler.enabled
    val hasChatMemory: Boolean = AgentFeatureType.CHAT_MEMORY in selected && chatMemory.enabled
    val hasAgentPersistence: Boolean = AgentFeatureType.AGENT_PERSISTENCE in selected && agentPersistence.enabled
    val hasTracing: Boolean = AgentFeatureType.TRACING in selected && tracing.enabled
    val hasLongTermMemory: Boolean = AgentFeatureType.LONG_TERM_MEMORY in selected && longTermMemory.enabled
    val hasDatadogExporter: Boolean = AgentFeatureType.OPEN_TELEMETRY_DATADOG in selected
    val hasLangfuseExporter: Boolean = AgentFeatureType.OPEN_TELEMETRY_LANGFUSE in selected
    val hasWeaveExporter: Boolean = AgentFeatureType.OPEN_TELEMETRY_WEAVE in selected
    val hasAnyOpenTelemetry: Boolean = hasDatadogExporter || hasLangfuseExporter || hasWeaveExporter
}
