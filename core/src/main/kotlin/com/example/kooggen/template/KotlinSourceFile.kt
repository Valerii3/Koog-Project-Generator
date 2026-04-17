package com.example.kooggen.template

class KotlinSourceFile(private val packageName: String) {
    private val imports = linkedSetOf<String>()
    private val declarations = mutableListOf<String>()

    fun addImport(importLine: String) {
        imports += importLine
    }

    fun addImports(importLines: Iterable<String>) {
        importLines.forEach { addImport(it) }
    }

    fun addDeclaration(code: String) {
        declarations += code.trimIndent().trim()
    }

    fun render(): String = buildString {
        appendLine("package $packageName")
        appendLine()
        imports.forEach { appendLine(it) }
        if (imports.isNotEmpty()) {
            appendLine()
        }
        declarations.forEachIndexed { index, declaration ->
            appendLine(declaration)
            if (index != declarations.lastIndex) {
                appendLine()
            }
        }
    }
}
