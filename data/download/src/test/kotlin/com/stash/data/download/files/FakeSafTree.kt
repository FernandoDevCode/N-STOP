package com.stash.data.download.files

import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.stash.core.data.prefs.StoragePreference
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.io.ByteArrayOutputStream

/**
 * A SAF tree in memory. Documents are mocks over maps; a document's URI is
 * derived from its path, so a document made again at a deleted one's name
 * gets the same URI, as on ExternalStorageProvider. Creating a name that
 * is already taken gets " (1)" added, as the real providers do. The caller
 * mocks [DocumentFile]'s statics (`mockkStatic(DocumentFile::class)`).
 */
internal class FakeSafTree(
    private val resolver: ContentResolver,
    storagePreference: StoragePreference,
) {
    val written = mutableMapOf<String, ByteArray>()
    val deleted = mutableListOf<String>()
    private val dirs = mutableMapOf<String, MutableMap<String, DocumentFile>>()
    private val root = dir("")

    /** The tree's root URI, as the user's folder pick hands it over. */
    val treeUri: Uri = mockk()

    init {
        every { storagePreference.externalTreeUri } returns flowOf(treeUri)
        every { DocumentFile.fromTreeUri(any(), treeUri) } returns root
        every { resolver.openOutputStream(any<Uri>()) } answers {
            val uri = firstArg<Uri>().toString()
            object : ByteArrayOutputStream() {
                override fun close() {
                    super.close()
                    written[uri] = toByteArray()
                }
            }
        }
    }

    fun uriOf(path: String): String =
        "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2F" +
            path.replace("/", "%2F")

    fun exists(path: String): Boolean =
        dirs[path.substringBeforeLast('/')]?.containsKey(path.substringAfterLast('/')) == true

    fun seedDir(path: String): DocumentFile =
        path.split('/').fold(root) { cursor, segment -> cursor.findFile(segment) ?: cursor.createDirectory(segment)!! }

    fun seed(path: String, bytes: ByteArray) {
        seedDir(path.substringBeforeLast('/')).createFile("audio/flac", path.substringAfterLast('/'))
        written[uriOf(path)] = bytes
    }

    private fun uri(path: String): Uri {
        val text = uriOf(path)
        return mockk { every { this@mockk.toString() } returns text }
    }

    private fun dir(path: String): DocumentFile {
        val children = linkedMapOf<String, DocumentFile>()
        dirs[path] = children
        val doc = mockk<DocumentFile>()
        every { doc.isDirectory } returns true
        every { doc.isFile } returns false
        every { doc.name } returns path.substringAfterLast('/')
        every { doc.uri } returns uri(path)
        every { doc.findFile(any()) } answers { children[firstArg()] }
        every { doc.listFiles() } answers { children.values.toTypedArray() }
        every { doc.createDirectory(any()) } answers {
            val name = firstArg<String>()
            dir(join(path, name)).also { children[name] = it }
        }
        every { doc.createFile(any(), any()) } answers {
            var name = secondArg<String>()
            if (name in children) name = "${name.substringBeforeLast('.')} (1).${name.substringAfterLast('.')}"
            file(join(path, name), children).also { children[name] = it }
        }
        return doc
    }

    private fun file(path: String, siblings: MutableMap<String, DocumentFile>): DocumentFile {
        val name = path.substringAfterLast('/')
        val text = uriOf(path)
        val doc = mockk<DocumentFile>()
        every { doc.isDirectory } returns false
        every { doc.isFile } returns true
        every { doc.name } returns name
        every { doc.uri } returns uri(path)
        every { doc.delete() } answers {
            siblings.remove(name)
            written.remove(text)
            deleted += text
            true
        }
        return doc
    }

    private fun join(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"
}
