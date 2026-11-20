package com.pocketaction.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SavedCapture(val id: Long, val text: String, val createdAt: Long)

class HistoryDb(context: Context) :
    SQLiteOpenHelper(context, "pocket_action.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE captures (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 has no migrations.
    }

    fun save(text: String): Long {
        val values = ContentValues().apply {
            put("body", text)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("captures", null, values)
    }

    fun recent(limit: Int? = null): List<SavedCapture> {
        val result = mutableListOf<SavedCapture>()
        readableDatabase.query(
            "captures",
            arrayOf("id", "body", "created_at"),
            null, null, null, null,
            "created_at DESC",
            limit?.coerceAtLeast(1)?.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SavedCapture(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getLong(2)
                )
            }
        }
        return result
    }

    // Search the complete saved set, not only the five items shown on the home screen.
    // Local capture histories are expected to remain modest in size; this avoids SQL wildcard surprises.
    fun search(query: String): List<SavedCapture> =
        recent().filter { it.text.contains(query, ignoreCase = true) }

    fun delete(id: Long): Int =
        writableDatabase.delete("captures", "id = ?", arrayOf(id.toString()))

    fun clearAll(): Int = writableDatabase.delete("captures", null, null)
}
