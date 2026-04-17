package com.example.kooggen.zip

import com.example.kooggen.template.GeneratedFile
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipProjectStreamWriter {
    fun write(outputStream: OutputStream, files: List<GeneratedFile>) {
        ZipOutputStream(outputStream).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.path.replace('\\', '/')))
                zip.write(file.content.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
