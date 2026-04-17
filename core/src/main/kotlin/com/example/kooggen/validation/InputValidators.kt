package com.example.kooggen.validation

import java.nio.file.Files
import java.nio.file.Path

object InputValidators {
    private val projectNameRegex = Regex("^[a-zA-Z][a-zA-Z0-9_-]{1,62}$")
    private val packageRegex = Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$")
    private val sessionIdRegex = Regex("^[A-Za-z0-9._:-]+$")
    private val identifierRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val typeNameRegex = Regex("^[A-Z][A-Za-z0-9_]*$")
    private val kotlinKeywords = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
        "true", "try", "typealias", "typeof", "val", "var", "when", "while"
    )

    fun validateProjectName(value: String): String? {
        if (!projectNameRegex.matches(value)) {
            return "Project name must start with a letter and contain only letters, digits, '-' or '_'."
        }
        return null
    }

    fun validatePackageName(value: String): String? {
        if (!packageRegex.matches(value)) {
            return "Package name must look like com.example.app and use valid Java/Kotlin identifiers."
        }
        return null
    }

    fun validateOutputPath(path: Path): String? {
        val normalized = path.toAbsolutePath().normalize()
        if (Files.exists(normalized) && !Files.isDirectory(normalized)) {
            return "Output path exists but is not a directory."
        }

        val parent = if (Files.exists(normalized)) normalized else normalized.parent
        if (parent == null || (!Files.exists(parent) && parent.parent == null)) {
            return "Output path has no valid parent directory."
        }

        return null
    }

    fun validateFunctionName(value: String): String? {
        if (!identifierRegex.matches(value)) {
            return "Function name must be a valid Kotlin identifier."
        }
        if (value in kotlinKeywords) {
            return "Function name cannot be a Kotlin keyword."
        }
        return null
    }

    fun validateParameterName(value: String): String? {
        if (!identifierRegex.matches(value)) {
            return "Parameter name must be a valid Kotlin identifier."
        }
        if (value in kotlinKeywords) {
            return "Parameter name cannot be a Kotlin keyword."
        }
        return null
    }

    fun validateClassName(value: String): String? {
        if (!typeNameRegex.matches(value)) {
            return "Class name must start with uppercase and be a valid Kotlin type name."
        }
        if (value in kotlinKeywords) {
            return "Class name cannot be a Kotlin keyword."
        }
        return null
    }

    fun validateRequiredDescription(value: String): String? {
        if (value.isBlank()) {
            return "Description is required."
        }
        return null
    }

    fun validateArtifact(value: String): String? {
        val packageError = validatePackageName(value)
        if (packageError != null) return packageError
        val lastSegment = value.substringAfterLast('.')
        if (lastSegment.isBlank()) {
            return "Artifact must have at least one segment after the last dot."
        }
        return null
    }

    fun validateSessionId(value: String): String? {
        if (value.isBlank()) {
            return "Session ID is required."
        }
        if (!sessionIdRegex.matches(value)) {
            return "Session ID can contain letters, digits, '.', '_', ':', and '-'."
        }
        return null
    }
}
