package com.trackr.app.data.local

import android.content.Context
import com.trackr.app.data.ImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

// @spec LS-BE-040
class LocalImageStore @Inject constructor(@ApplicationContext context: Context) : ImageStore {
    private val dir = File(context.filesDir, "images").also { it.mkdirs() }

    override fun newFile(extension: String): File = File(dir, "${UUID.randomUUID()}.$extension")

    override fun delete(absolutePath: String) { File(absolutePath).delete() }

    override fun allStoredPaths(): List<String> = dir.listFiles()?.map { it.absolutePath } ?: emptyList()
}
