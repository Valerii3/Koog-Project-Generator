package com.example.kooggen.cli

import com.example.kooggen.template.TemplateRegistry
import com.example.kooggen.zip.ZipProjectWriter
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val cli = InteractiveCli()
    val spec = cli.collectSpec()

    val archivePath: Path = Path.of(".").toAbsolutePath().normalize().resolve(spec.archiveFileName)

    if (Files.exists(archivePath) && !cli.confirmOverwrite(archivePath)) {
        println("Cancelled. Existing archive was not overwritten.")
        return
    }

    val template = TemplateRegistry().resolve(spec.templateType)
    val files = template.render(spec)

    ZipProjectWriter().write(archivePath, files)

    println()
    println("Project ZIP generated successfully.")
    println("Archive: ${archivePath.toAbsolutePath()}")
    println("Template: ${spec.templateType.cliLabel}")
    spec.plannerType?.let { println("Planner: ${it.cliLabel}") }
    println("Provider: ${spec.llmProvider.displayName}")
    val toolingSummary = if (spec.tooling.enabled) {
        spec.tooling.selectedCapabilities.joinToString { it.cliLabel }
    } else {
        "None"
    }
    val featureSummary = if (spec.features.selected.isNotEmpty()) {
        spec.features.selected.joinToString { it.cliLabel }
    } else {
        "None"
    }
    println("Tools: $toolingSummary")
    println("Features: $featureSummary")
    println()
    println("Next steps:")
    println("1. unzip '${spec.archiveFileName}'")
    println("2. cd '${spec.projectName}'")
    if (spec.llmProvider.requiresApiKey) {
        val envVar = requireNotNull(spec.llmProvider.envVarName)
        println("3. export $envVar=...")
        println("4. ./gradlew run")
    } else {
        println("3. make sure Ollama is running with llama3.2 available")
        println("4. ./gradlew run")
    }
}
