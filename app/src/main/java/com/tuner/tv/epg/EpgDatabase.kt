package com.tuner.tv.epg

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Programme storage. Plain SQLite: small, fast and no extra build tooling. */
class EpgDatabase(context: Context) : SQLiteOpenHelper(context.applicationContext, "epg.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE programmes (ch TEXT NOT NULL, start INTEGER NOT NULL, stop INTEGER NOT NULL, title TEXT NOT NULL, descr TEXT)")
        db.execSQL("CREATE INDEX idx_ch_start ON programmes (ch, start)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS programmes")
        onCreate(db)
    }

    /**
     * Replaces the whole guide inside one transaction. If [fill] throws part-way,
     * the old guide is kept.
     */
    fun replaceAll(fill: ((Programme) -> Unit) -> Unit): Int {
        val db = writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            db.delete("programmes", null, null)
            val st = db.compileStatement("INSERT INTO programmes (ch, start, stop, title, descr) VALUES (?,?,?,?,?)")
            fill { p ->
                st.clearBindings()
                st.bindString(1, p.channelKey)
                st.bindLong(2, p.start)
                st.bindLong(3, p.stop)
                st.bindString(4, p.title)
                if (p.desc != null) st.bindString(5, p.desc) else st.bindNull(5)
                st.executeInsert()
                count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    fun range(keys: List<String>, from: Long, to: Long): List<Programme> {
        if (keys.isEmpty()) return emptyList()
        val marks = keys.joinToString(",") { "?" }
        val args = keys.toTypedArray() + arrayOf(to.toString(), from.toString())
        val out = ArrayList<Programme>()
        readableDatabase.rawQuery(
            "SELECT ch, start, stop, title, descr FROM programmes WHERE ch IN ($marks) AND start < ? AND stop > ? ORDER BY start",
            args,
        ).use { c ->
            var lastStart = Long.MIN_VALUE
            while (c.moveToNext()) {
                val start = c.getLong(1)
                if (start == lastStart) continue // same programme matched by two keys
                lastStart = start
                out += Programme(c.getString(0), start, c.getLong(2), c.getString(3), if (c.isNull(4)) null else c.getString(4))
            }
        }
        return out
    }

    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM programmes", null).use {
        if (it.moveToFirst()) it.getLong(0) else 0
    }
}
