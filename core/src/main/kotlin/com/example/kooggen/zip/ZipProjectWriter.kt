package com.example.kooggen.zip

import com.example.kooggen.template.GeneratedFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipProjectWriter {
    fun write(archivePath: Path, files: List<GeneratedFile>) {
        val parent = archivePath.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }

        ZipOutputStream(Files.newOutputStream(archivePath)).use { zip ->
            files.forEach { file ->
                val normalized = file.path.replace('\\', '/')
                val entry = ZipEntry(normalized)
                zip.putNextEntry(entry)
                zip.write(file.content.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
