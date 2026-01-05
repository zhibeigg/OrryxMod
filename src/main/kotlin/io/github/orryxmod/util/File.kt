package io.github.orryxmod.util

import io.github.orryxmod.core.FileManager.releaseResourceFile
import java.io.File

internal fun files(path: String, vararg defs: String, callback: (File) -> Unit) {
    val file = File("resourcepacks", path)
    if (!file.exists()) {
        defs.forEach {
            releaseResourceFile("$path/$it", replace = false)
        }
    }
    getFiles(file).forEach { callback(it) }
}

fun newFile(file: File, create: Boolean = true, folder: Boolean = false): File {
    val parentFile = file.parentFile
    if (parentFile != null && parentFile.notfound()) {
        parentFile.mkdirs()
    }
    if (file.notfound() && create) {
        if (folder) {
            file.mkdirs()
        } else {
            file.createNewFile()
        }
    }
    return file
}

fun File.notfound(): Boolean {
    return !exists()
}

internal fun getFiles(file: File): List<File> {
    if (!file.exists()) return emptyList()

    return when {
        file.isDirectory -> file.listFiles()?.flatMap { getFiles(it) } ?: emptyList()
        file.extension.equals("png", ignoreCase = true) -> listOf(file)
        else -> emptyList()
    }
}
