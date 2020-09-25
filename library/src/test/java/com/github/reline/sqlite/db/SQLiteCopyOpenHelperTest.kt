package com.github.reline.sqlite.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test

import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SQLiteCopyOpenHelperTest {
    @Test
    fun copyEmptyDatabaseFileFromAssetPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .callback(TestCallback())
            .name("empty.db")
            .build()
        SQLiteCopyOpenHelper.Factory(context, CopyFromAssetPath("EmptyDatabaseFile.db"), FrameworkSQLiteOpenHelperFactory())
            .create(config)
            .writableDatabase
            .close()
    }

    @Test
    fun destructivelyMigrate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configBuilder = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("1.db")

        SQLiteCopyOpenHelper.Factory(context, CopyFromAssetPath("1.db"), FrameworkSQLiteOpenHelperFactory())
            .create(configBuilder.callback(TestCallback(1)).build())
            .readableDatabase.use { db ->
                assertEquals(1, db.version)
                db.beginTransaction()
                val cursor = db.query("""SELECT value FROM Test LIMIT 1""")
                cursor.moveToFirst()
                val value = cursor.getInt(0)
                assertEquals(1, value)
                db.endTransaction()
            }

        SQLiteCopyOpenHelper.Factory(context, CopyFromAssetPath("2.db"), FrameworkSQLiteOpenHelperFactory())
            .create(configBuilder.callback(TestCallback(2)).build())
            .readableDatabase.use { db ->
                assertEquals(2, db.version)
                db.beginTransaction()
                val cursor = db.query("""SELECT value FROM Test LIMIT 1""")
                cursor.moveToFirst()
                val value = cursor.getInt(0)
                assertEquals(2, value)
                db.endTransaction()
            }
    }
}

private class TestCallback(version: Int = 1) : SupportSQLiteOpenHelper.Callback(version) {
    override fun onCreate(db: SupportSQLiteDatabase) {
        // TODO("Not yet implemented")
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // TODO("Not yet implemented")
    }
}