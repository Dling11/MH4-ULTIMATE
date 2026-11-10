package org.mh4u.port.gameimport

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/** A read-only SAF source; choosing an archive does not require broad storage access. */
class DocumentGameSource(private val resolver: ContentResolver, private val uri: Uri) : GameImportSource {
    private val details = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) null else {
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
            (if (nameColumn >= 0) cursor.getString(nameColumn) else null) to
                (if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else -1L)
        }
    } ?: throw IOException("The selected file could not be opened. Save it to Internal storage and choose it again.")
    override val name: String = details.first ?: throw IOException("The selected file has no name.")
    override val size: Long = details.second
    override fun openStream(): InputStream = resolver.openInputStream(uri)
        ?: throw IOException("The selected game file could not be opened.")

    override fun openChannel(): SeekableByteChannel {
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: throw IOException("The archive could not be opened.")
        val stream = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        try {
            stream.channel.size()
            stream.channel.position(0)
        } catch (e: Exception) {
            stream.close()
            throw IOException("This file provider cannot read archives directly. Save the ZIP or 7Z to Internal storage, then choose it again.", e)
        }
        return object : SeekableByteChannel {
            private val channel = stream.channel
            override fun read(dst: ByteBuffer): Int = channel.read(dst)
            override fun position(): Long = channel.position()
            override fun position(newPosition: Long): SeekableByteChannel { channel.position(newPosition); return this }
            override fun size(): Long = channel.size()
            override fun isOpen(): Boolean = channel.isOpen
            override fun close() = stream.close()
            override fun write(src: ByteBuffer): Int = throw UnsupportedOperationException("Read only")
            override fun truncate(size: Long): SeekableByteChannel = throw UnsupportedOperationException("Read only")
        }
    }
}
