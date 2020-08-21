package com.github.reline.example

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.provider.BaseColumns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

data class Entry(val id: Long, val title: String, val subtitle: String)

class MainActivity : AppCompatActivity() {
    private lateinit var dbHelper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = FeedReaderDbFactory(applicationContext, "password".toByteArray()).create()

        // Gets the data repository in write mode
        db = dbHelper.writableDatabase
    }

    private fun getContents(): MutableList<Entry> {
        val query = StringBuilder()
            .appendLine("SELECT ${BaseColumns._ID}, ${FeedReaderContract.FeedEntry.COLUMN_NAME_TITLE}, ${FeedReaderContract.FeedEntry.COLUMN_NAME_SUBTITLE}")
            .appendLine("FROM ${FeedReaderContract.FeedEntry.TABLE_NAME}")
            .appendLine("""WHERE ${FeedReaderContract.FeedEntry.COLUMN_NAME_TITLE} = "title"""")
            .toString()
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

    fun onButtonClicked(v: View) {
        // Create a new map of values, where column names are the keys
        val values = ContentValues().apply {
            put(FeedReaderContract.FeedEntry.COLUMN_NAME_TITLE, "title")
            put(FeedReaderContract.FeedEntry.COLUMN_NAME_SUBTITLE, "subtitle")
        }
        // Insert the new row, returning the primary key value of the new row
        val newRowId = db.insert(FeedReaderContract.FeedEntry.TABLE_NAME, SQLiteDatabase.CONFLICT_FAIL, values)
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }
}