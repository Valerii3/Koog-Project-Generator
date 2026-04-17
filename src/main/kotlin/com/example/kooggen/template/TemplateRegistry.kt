package com.example.kooggen.template

import com.example.kooggen.model.AgentTemplateType

class TemplateRegistry(
    private val templates: Map<AgentTemplateType, ProjectTemplate> = mapOf(
        AgentTemplateType.BASIC to BasicAgentTemplate(),
        AgentTemplateType.FUNCTIONAL to FunctionalAgentTemplate(),
        AgentTemplateType.PLANNER to PlannerAgentTemplate()
    )
) {
    fun resolve(type: AgentTemplateType): ProjectTemplate =
        templates[type] ?: error("Template is not implemented for type: $type")
}
