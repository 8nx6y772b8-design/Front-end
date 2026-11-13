package com.tuner.tv.rec

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tuner.tv.data.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Schedule(
    val id: String,
    val channelId: Int,
    val channelName: String,
    val url: String,
    val start: Long,
    val end: Long,
    val title: String?,
) {
    fun toJson() = JSONObject().put("id", id).put("channelId", channelId).put("channelName", channelName)
        .put("url", url).put("start", start).put("end", end).put("title", title ?: "")

    companion object {
        fun new(channelId: Int, channelName: String, url: String, start: Long, end: Long, title: String?) =
            Schedule(UUID.randomUUID().toString(), channelId, channelName, url, start, end, title)

        fun fromJson(o: JSONObject) = Schedule(
            o.getString("id"), o.optInt("channelId", -1), o.getString("channelName"), o.getString("url"),
            o.getLong("start"), o.getLong("end"), o.optString("title").ifBlank { null },
        )
    }
}

/** Saved timed recordings + their alarms. */
class ScheduleStore(private val context: Context) {
    private val settings = Settings(context)

    fun all(): List<Schedule> = runCatching {
        val arr = JSONArray(settings.schedulesJson)
        (0 until arr.length()).map { Schedule.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList()).sortedBy { it.start }

    private fun save(list: List<Schedule>) {
        settings.schedulesJson = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }

    fun add(s: Schedule) {
        save(all() + s)
        arm(s)
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
        alarmManager().cancel(pendingIntent(id))
    }

    fun get(id: String) = all().firstOrNull { it.id == id }

    /** Re-arms every future schedule (after a reboot or app update) and drops finished ones. */
    fun rearmAll() {
        val now = System.currentTimeMillis()
        val keep = all().filter { it.end > now }
        save(keep)
        keep.forEach { arm(it) }
    }

    private fun alarmManager() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(id: String): PendingIntent = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, RecordAlarmReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun arm(s: Schedule) {
        val am = alarmManager()
        val at = maxOf(s.start, System.currentTimeMillis() + 1_000)
        val pi = pendingIntent(s.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    companion object {
        const val EXTRA_ID = "schedule_id"
    }
}

/** Fires when a timed recording is due and hands it to the recording service. */
class RecordAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ScheduleStore.EXTRA_ID) ?: return
        val store = ScheduleStore(context)
        val s = store.get(id) ?: return
        store.remove(id)
        if (s.end <= System.currentTimeMillis()) return
        RecorderService.start(
            context,
            RecordRequest(s.id, s.channelId, s.channelName, s.url, endAt = s.end, title = s.title),
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ScheduleStore(context).rearmAll()
    }
}
