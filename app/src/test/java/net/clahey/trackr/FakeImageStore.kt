package net.clahey.trackr

import net.clahey.trackr.data.ImageStore
import java.io.File
import java.util.UUID

class FakeImageStore : ImageStore {
    private val stored = mutableListOf<String>()
    private val deleted = mutableListOf<String>()

    override fun newFile(extension: String): File {
        val file = File("/fake/images/${UUID.randomUUID()}.$extension")
        stored.add(file.absolutePath)
        return file
    }

    override fun delete(absolutePath: String) {
        stored.remove(absolutePath)
        deleted.add(absolutePath)
    }

    override fun allStoredPaths(): List<String> = stored.toList()

    fun wasDeleted(path: String): Boolean = path in deleted
}
