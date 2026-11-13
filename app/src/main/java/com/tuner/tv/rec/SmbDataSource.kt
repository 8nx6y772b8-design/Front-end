@file:OptIn(UnstableApi::class)

package com.tuner.tv.rec

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.File
import java.io.IOException
import java.util.EnumSet

/** Lets the player read recordings straight off the network drive (smb:// links). */
class SmbDataSource(private val cfg: NasConfig) : BaseDataSource(/* isNetwork = */ true) {

    private var session: SmbSession? = null
    private var file: File? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        // smb://host/share/folder/name -> folder\name
        val segments = dataSpec.uri.pathSegments
        if (segments.size < 2) throw IOException("Bad recording link")
        val path = segments.drop(1).joinToString("\\")
        try {
            val s = SmbSession(cfg)
            session = s
            val f = s.share.openFile(
                path, EnumSet.of(AccessMask.GENERIC_READ), EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, EnumSet.noneOf(SMB2CreateOptions::class.java),
            )
            file = f
            val size = f.fileInformation.standardInformation.endOfFile
            position = dataSpec.position
            remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else size - position
        } catch (e: Exception) {
            close()
            throw IOException(SmbSession.friendly(e), e)
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0) return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), remaining).toInt()
        val n = file?.read(buffer, position, offset, toRead) ?: return C.RESULT_END_OF_INPUT
        if (n <= 0) return C.RESULT_END_OF_INPUT
        position += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { file?.close() }
        session?.close()
        file = null
        session = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val cfg: NasConfig) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(cfg)
    }
}
