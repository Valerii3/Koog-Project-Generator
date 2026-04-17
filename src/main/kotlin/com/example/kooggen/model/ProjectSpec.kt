package com.example.kooggen.model

import java.nio.file.Path

data class ProjectSpec(
    val projectName: String,
    val outputDir: Path,
    val packageName: String,
    val templateType: AgentTemplateType,
    val llmProvider: LlmProvider = LlmProvider.OPENAI,
    val tooling: ProjectToolingSpec = ProjectToolingSpec(),
    val features: ProjectFeatureSpec = ProjectFeatureSpec(),
    val plannerType: PlannerType? = null
) {
    val archiveFileName: String = "${projectName}-${templateType.archiveSuffix}.zip"
    val archivePath: Path = outputDir.resolve(archiveFileName)
    val packagePath: String = packageName.replace('.', '/')
}
