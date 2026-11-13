package com.tuner.tv.epg

data class Programme(
    val channelKey: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String?,
) {
    fun isOnAt(t: Long) = t in start until stop
    fun progress(now: Long): Float =
        if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}
