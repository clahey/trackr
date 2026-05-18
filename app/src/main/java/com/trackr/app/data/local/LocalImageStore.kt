package com.trackr.app.data.local

import com.trackr.app.data.ImageStore
import java.io.File
import java.util.UUID

// @spec LS-BE-040
class LocalImageStore(filesDir: File) : ImageStore {
    private val dir = File(filesDir, "images").also { it.mkdirs() }

    override fun newFile(extension: String): File = File(dir, "${UUID.randomUUID()}.$extension")

    override fun delete(absolutePath: String) { File(absolutePath).delete() }

    override fun allStoredPaths(): List<String> = dir.listFiles()?.map { it.absolutePath } ?: emptyList()
}
