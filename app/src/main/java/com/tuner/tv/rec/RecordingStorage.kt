package com.tuner.tv.rec

import android.content.Context
import android.net.Uri
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.tuner.tv.data.Settings
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

data class RecordingFile(val name: String, val size: Long, val modified: Long, val uri: Uri) {
    val title: String get() = name.substringBeforeLast('.')
}

/** Where recordings go: the network drive if one is set up, otherwise the Fire Stick itself. */
interface RecordingStorage {
    val label: String
    fun openWrite(fileName: String): OutputStream
    fun list(): List<RecordingFile>
    fun delete(file: RecordingFile)
    /** Throws with a readable message if the location can't be written to. */
    fun test()

    companion object {
        fun from(context: Context, settings: Settings): RecordingStorage {
            val nas = NasConfig.parse(settings.nasPath, settings.nasUser, settings.nasPass)
            return if (nas != null) SmbStorage(nas) else LocalStorage(context)
        }
    }
}

val RECORDING_EXTENSIONS = setOf("ts", "mp4", "mkv")

// ---------------------------------------------------------------- Network drive (SMB)

data class NasConfig(
    val host: String,
    val share: String,
    val folder: String,   // may be "" for the share root; uses backslashes
    val user: String,
    val pass: String,
    val domain: String,
) {
    fun smbPath(name: String) = if (folder.isEmpty()) name else "$folder\\$name"

    fun uriFor(name: String): Uri = Uri.Builder().scheme("smb").encodedAuthority(host)
        .appendPath(share).apply { folder.split('\\').filter { it.isNotEmpty() }.forEach { appendPath(it) } }
        .appendPath(name).build()

    val display: String get() = "\\\\$host\\$share" + (if (folder.isEmpty()) "" else "\\$folder")

    companion object {
        /** Accepts \\NAS\share\folder, smb://NAS/share/folder or NAS/share/folder. User may be DOMAIN\user. */
        fun parse(path: String, user: String, pass: String): NasConfig? {
            val parts = path.trim().replace('\\', '/').removePrefix("smb:").trim('/')
                .split('/').filter { it.isNotBlank() }
            if (parts.size < 2) return null
            val (domain, name) = if ('\\' in user) user.substringBefore('\\') to user.substringAfter('\\') else "" to user
            return NasConfig(parts[0], parts[1], parts.drop(2).joinToString("\\"), name, pass, domain)
        }
    }
}

/** One open SMB connection. Close it to release everything. */
class SmbSession(val cfg: NasConfig) : Closeable {
    private val client = SMBClient(
        SmbConfig.builder()
            .withSecurityProvider(BCSecurityProvider())   // Android lacks MD4, which NTLM needs
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(60, TimeUnit.SECONDS)
            .build()
    )
    private val connection: Connection
    private val session: Session
    val share: DiskShare

    init {
        try {
            connection = client.connect(cfg.host)
            val auth = if (cfg.user.isBlank()) AuthenticationContext.guest()
            else AuthenticationContext(cfg.user, cfg.pass.toCharArray(), cfg.domain)
            session = connection.authenticate(auth)
            share = session.connectShare(cfg.share) as? DiskShare
                ?: throw IOException("\"${cfg.share}\" isn't a shared folder")
        } catch (e: Exception) {
            client.close()
            throw IOException(friendly(e), e)
        }
    }

    fun ensureFolder() {
        if (cfg.folder.isEmpty()) return
        var path = ""
        for (part in cfg.folder.split('\\')) {
            path = if (path.isEmpty()) part else "$path\\$part"
            if (!share.folderExists(path)) share.mkdir(path)
        }
    }

    override fun close() {
        runCatching { share.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }

    companion object {
        fun friendly(e: Throwable): String {
            val m = (e.message ?: e.javaClass.simpleName)
            return when {
                "STATUS_LOGON_FAILURE" in m -> "Wrong username or password for the network drive"
                "STATUS_BAD_NETWORK_NAME" in m -> "Shared folder not found on the network drive"
                "STATUS_ACCESS_DENIED" in m -> "The network drive refused access. Check the user can write to that folder"
                e is java.net.UnknownHostException || "UnknownHost" in m -> "Can't find the network drive. Try its IP address, e.g. 192.168.1.50"
                e is java.net.ConnectException || "timed out" in m.lowercase() -> "Can't reach the network drive. Is it on, and is file sharing (SMB) enabled?"
                else -> m
            }
        }
    }
}

class SmbStorage(private val cfg: NasConfig) : RecordingStorage {
    override val label = cfg.display

    override fun openWrite(fileName: String): OutputStream {
        val s = SmbSession(cfg)
        try {
            s.ensureFolder()
            val file = s.share.openFile(
                cfg.smbPath(fileName),
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                EnumSet.noneOf(SMB2CreateOptions::class.java),
            )
            val raw = file.outputStream
            return object : BufferedOutputStream(raw, 1 shl 20) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        runCatching { file.close() }
                        s.close()
                    }
                }
            }
        } catch (e: Exception) {
            s.close()
            throw IOException(SmbSession.friendly(e), e)
        }
    }

    override fun list(): List<RecordingFile> = SmbSession(cfg).use { s ->
        if (cfg.folder.isNotEmpty() && !s.share.folderExists(cfg.folder)) return emptyList()
        s.share.list(cfg.folder).filter {
            it.fileName.substringAfterLast('.', "").lowercase() in RECORDING_EXTENSIONS
        }.map {
            RecordingFile(it.fileName, it.endOfFile, it.lastWriteTime.toEpochMillis(), cfg.uriFor(it.fileName))
        }.sortedByDescending { it.modified }
    }

    override fun delete(file: RecordingFile) {
        SmbSession(cfg).use { it.share.rm(cfg.smbPath(file.name)) }
    }

    override fun test() {
        SmbSession(cfg).use { s ->
            s.ensureFolder()
            val probe = ".tuner-test"
            val f = s.share.openFile(
                cfg.smbPath(probe), EnumSet.of(AccessMask.GENERIC_WRITE), EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ), SMB2CreateDisposition.FILE_OVERWRITE_IF,
                EnumSet.noneOf(SMB2CreateOptions::class.java),
            )
            f.outputStream.use { it.write("ok".toByteArray()) }
            f.close()
            s.share.rm(cfg.smbPath(probe))
        }
    }
}

// ---------------------------------------------------------------- On the Fire Stick

class LocalStorage(context: Context) : RecordingStorage {
    private val dir = (context.getExternalFilesDir("Recordings") ?: File(context.filesDir, "Recordings")).apply { mkdirs() }
    override val label = "This Fire Stick (limited space)"

    override fun openWrite(fileName: String): OutputStream =
        BufferedOutputStream(FileOutputStream(File(dir, fileName)), 1 shl 20)

    override fun list(): List<RecordingFile> = (dir.listFiles() ?: emptyArray())
        .filter { it.extension.lowercase() in RECORDING_EXTENSIONS }
        .map { RecordingFile(it.name, it.length(), it.lastModified(), Uri.fromFile(it)) }
        .sortedByDescending { it.modified }

    override fun delete(file: RecordingFile) {
        File(dir, file.name).delete()
    }

    override fun test() {
        if (!dir.canWrite()) throw IOException("Can't write to the Fire Stick's storage")
    }
}
