package net.clahey.trackr.data

import java.io.File

interface ImageStore {
    fun newFile(extension: String = "jpg"): File
    fun delete(absolutePath: String)
    fun allStoredPaths(): List<String>
}
