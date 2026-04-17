package com.example.kooggen.model

data class ProjectSpec(
    val artifact: String,
    val projectName: String,
    val packageName: String,
    val templateType: AgentTemplateType,
    val llmProvider: LlmProvider = LlmProvider.OPENAI,
    val tooling: ProjectToolingSpec = ProjectToolingSpec(),
    val features: ProjectFeatureSpec = ProjectFeatureSpec(),
    val plannerType: PlannerType? = null
) {
    val archiveFileName: String = "$projectName.zip"
    val packagePath: String = packageName.replace('.', '/')

    companion object {
        fun fromArtifact(
            artifact: String,
            templateType: AgentTemplateType,
            llmProvider: LlmProvider = LlmProvider.OPENAI,
            tooling: ProjectToolingSpec = ProjectToolingSpec(),
            features: ProjectFeatureSpec = ProjectFeatureSpec(),
            plannerType: PlannerType? = null
        ): ProjectSpec {
            val projectName = artifact.substringAfterLast('.')
            return ProjectSpec(
                artifact = artifact,
                projectName = projectName,
                packageName = artifact,
                templateType = templateType,
                llmProvider = llmProvider,
                tooling = tooling,
                features = features,
                plannerType = plannerType
            )
        }
    }
}
