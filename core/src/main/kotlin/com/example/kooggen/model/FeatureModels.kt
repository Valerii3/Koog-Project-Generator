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

data class ProjectFeatureSpec(
    val selected: Set<AgentFeatureType> = emptySet(),
    val eventHandler: EventHandlerFeatureConfig = EventHandlerFeatureConfig(),
    val chatMemory: ChatMemoryFeatureConfig = ChatMemoryFeatureConfig(),
    val agentPersistence: AgentPersistenceFeatureConfig = AgentPersistenceFeatureConfig(),
    val tracing: TracingFeatureConfig = TracingFeatureConfig()
) {
    val hasEventHandler: Boolean = AgentFeatureType.EVENT_HANDLER in selected && eventHandler.enabled
    val hasChatMemory: Boolean = AgentFeatureType.CHAT_MEMORY in selected && chatMemory.enabled
    val hasAgentPersistence: Boolean = AgentFeatureType.AGENT_PERSISTENCE in selected && agentPersistence.enabled
    val hasTracing: Boolean = AgentFeatureType.TRACING in selected && tracing.enabled
}
