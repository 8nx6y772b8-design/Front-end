package com.tuner.tv.rec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.tuner.tv.R
import com.tuner.tv.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class RecordRequest(
    val id: String = UUID.randomUUID().toString(),
    val channelId: Int,
    val channelName: String,
    val url: String,
    /** 0 = until stopped (capped at 4 hours) */
    val endAt: Long = 0,
    val title: String? = null,
)

data class ActiveRecording(
    val id: String,
    val channelId: Int,
    val channelName: String,
    val fileName: String,
    val startedAt: Long,
    val endAt: Long,
    val bytes: Long = 0,
    val error: String? = null,
)

/** Live recording status for the UI. */
object Recordings {
    private val _active = MutableStateFlow<Map<String, ActiveRecording>>(emptyMap())
    val active: StateFlow<Map<String, ActiveRecording>> = _active

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages

    internal fun put(r: ActiveRecording) = _active.update { it + (r.id to r) }
    internal fun remove(id: String) = _active.update { it - id }
    internal fun bytes(id: String, n: Long) = _active.update { m ->
        m[id]?.let { m + (id to it.copy(bytes = it.bytes + n)) } ?: m
    }
    internal fun message(text: String) {
        _messages.value = text
    }
    fun consumeMessage() {
        _messages.value = null
    }
}

/**
 * Foreground service that does the recording, so it keeps going when you're watching
 * something else, browsing, or the Fire Stick screen is off.
 */
class RecorderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val stopFlags = ConcurrentHashMap<String, Boolean>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground("Recording…")
        when (intent?.action) {
            ACTION_START -> intent.toRequest()?.let { start(it) }
            ACTION_STOP -> intent.getStringExtra(EXTRA_ID)?.let { stopFlags[it] = true }
        }
        if (jobs.isEmpty() && intent?.action != ACTION_START) shutdownIfIdle()
        return START_NOT_STICKY
    }

    private fun start(req: RecordRequest) {
        if (jobs.containsKey(req.id)) return
        acquireLocks()
        val settings = Settings(this)
        val stamp = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(Date())
        val endAt = if (req.endAt > 0) req.endAt else System.currentTimeMillis() + 4 * 3_600_000L
        stopFlags[req.id] = false

        jobs[req.id] = scope.launch {
            var active = ActiveRecording(req.id, req.channelId, req.channelName, "", System.currentTimeMillis(), endAt)
            Recordings.put(active)
            updateNotification()
            try {
                val recorder = StreamRecorder(req.url)
                val probe = recorder.probe()
                val base = sanitize(req.channelName) + " " + stamp + (req.title?.let { " - " + sanitize(it) } ?: "")
                val fileName = "$base.${probe.extension}"
                active = active.copy(fileName = fileName)
                Recordings.put(active)
                val storage = RecordingStorage.from(this@RecorderService, settings)
                storage.openWrite(fileName).use { out ->
                    recorder.record(
                        probe, out,
                        shouldStop = { stopFlags[req.id] == true || System.currentTimeMillis() >= endAt },
                        onBytes = { Recordings.bytes(req.id, it) },
                    )
                }
                Recordings.message("Recording saved: ${req.channelName}")
            } catch (e: Exception) {
                Recordings.message("Recording failed (${req.channelName}): ${e.message ?: e.javaClass.simpleName}")
            } finally {
                Recordings.remove(req.id)
                jobs.remove(req.id)
                stopFlags.remove(req.id)
                updateNotification()
                shutdownIfIdle()
            }
        }
    }

    private fun shutdownIfIdle() {
        if (jobs.isNotEmpty()) return
        releaseLocks()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun updateNotification() {
        val names = Recordings.active.value.values.joinToString(", ") { it.channelName }
        if (names.isNotEmpty()) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification("Recording: $names"))
        }
    }

    private fun goForeground(text: String) {
        val n = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recordings", NotificationManager.IMPORTANCE_LOW))
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("Tuner").setContentText(text).setSmallIcon(R.drawable.ic_rec).setOngoing(true).build()
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tuner:recording").apply { acquire(6 * 3_600_000L) }
        }
        if (wifiLock == null) {
            wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tuner:recording").apply { acquire() }
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
    }

    override fun onDestroy() {
        stopFlags.keys.forEach { stopFlags[it] = true }
        scope.cancel()
        releaseLocks()
        super.onDestroy()
    }

    private fun Intent.toRequest(): RecordRequest? {
        val url = getStringExtra(EXTRA_URL) ?: return null
        return RecordRequest(
            id = getStringExtra(EXTRA_ID) ?: UUID.randomUUID().toString(),
            channelId = getIntExtra(EXTRA_CHANNEL_ID, -1),
            channelName = getStringExtra(EXTRA_NAME) ?: "Recording",
            url = url,
            endAt = getLongExtra(EXTRA_END, 0),
            title = getStringExtra(EXTRA_TITLE),
        )
    }

    companion object {
        private const val CHANNEL = "recordings"
        private const val NOTIF_ID = 42
        private const val ACTION_START = "com.tuner.tv.REC_START"
        private const val ACTION_STOP = "com.tuner.tv.REC_STOP"
        private const val EXTRA_ID = "id"
        private const val EXTRA_URL = "url"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_CHANNEL_ID = "channel_id"
        private const val EXTRA_END = "end"
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, r: RecordRequest) {
            val i = Intent(context, RecorderService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_ID, r.id).putExtra(EXTRA_URL, r.url).putExtra(EXTRA_NAME, r.channelName)
                .putExtra(EXTRA_CHANNEL_ID, r.channelId).putExtra(EXTRA_END, r.endAt).putExtra(EXTRA_TITLE, r.title)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context, id: String) {
            val i = Intent(context, RecorderService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_ID, id)
            ContextCompat.startForegroundService(context, i)
        }

        fun sanitize(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().take(80)
    }
}
