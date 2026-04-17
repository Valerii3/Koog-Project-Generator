package com.example.kooggen.template

import com.example.kooggen.model.AgentTemplateType
import com.example.kooggen.model.ProjectSpec

interface ProjectTemplate {
    val type: AgentTemplateType
    fun render(spec: ProjectSpec): List<GeneratedFile>
}
