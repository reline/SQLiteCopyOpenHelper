package io.github.reline.example

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import androidx.sqlite.db.SupportSQLiteOpenHelper

class FeedDao(private val helper: SupportSQLiteOpenHelper) {
    val db get() = helper.writableDatabase

    fun getContents(): MutableList<Entry> {
        val query = StringBuilder()
            .append("SELECT ${BaseColumns._ID}, ${FeedReaderContract.FeedEntry.COLUMN_NAME_TITLE}, ${FeedReaderContract.FeedEntry.COLUMN_NAME_SUBTITLE}")
            .append("FROM ${FeedReaderContract.FeedEntry.TABLE_NAME}")
            .append("""WHERE ${FeedReaderContract.FeedEntry.COLUMN_NAME_TITLE} = "title"""")
            .toString()
        db.use {
            val cursor = db.query(query)

            val items = mutableListOf<Entry>()
            with(cursor) {
                while (moveToNext()) {
                    val itemId = getLong(getColumnIndexOrThrow(BaseColumns._ID))
                    val title = getString(getColumnIndexOrThrow(FeedReaderContract.FeedEntry.COLUMN_NAME_TITLE))
                    val subtitle = getString(getColumnIndexOrThrow(FeedReaderContract.FeedEntry.COLUMN_NAME_SUBTITLE))
                    items.add(Entry(itemId, title, subtitle))
                }
            }
            return items
        }
    }

    fun insertEntry(title: String, subtitle: String) {
        // Create a new map of values, where column names are the keys
        val values = ContentValues().apply {
            put(FeedReaderContract.FeedEntry.COLUMN_NAME_TITLE, title)
            put(FeedReaderContract.FeedEntry.COLUMN_NAME_SUBTITLE, subtitle)
        }
        // Insert the new row
        db.insert(FeedReaderContract.FeedEntry.TABLE_NAME, SQLiteDatabase.CONFLICT_FAIL, values)
    }
}