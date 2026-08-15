package net.clahey.trackr.data.local

import android.content.Context
import net.clahey.trackr.data.ImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

// @spec LS-BE-040
class LocalImageStore @Inject constructor(@ApplicationContext context: Context) : ImageStore {
    private val dir = File(context.filesDir, "images").also { it.mkdirs() }

    // @spec LS-BE-060
    override fun newFile(extension: String): File = File(dir, "${UUID.randomUUID()}.$extension")

    // File.delete() returns false rather than throwing when the path is absent — the no-op
    // LS-BE-061 requires.
    // @spec LS-BE-061
    override fun delete(absolutePath: String) { File(absolutePath).delete() }

    // @spec LS-BE-062
    override fun allStoredPaths(): List<String> = dir.listFiles()?.map { it.absolutePath } ?: emptyList()
}
