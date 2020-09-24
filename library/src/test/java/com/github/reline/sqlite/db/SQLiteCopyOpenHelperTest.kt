package com.github.reline.sqlite.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}

private class TestCallback(version: Int = 1) : SupportSQLiteOpenHelper.Callback(version) {
    override fun onCreate(db: SupportSQLiteDatabase) {
        // TODO("Not yet implemented")
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // TODO("Not yet implemented")
    }
}