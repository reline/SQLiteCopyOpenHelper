package com.github.reline.example

import android.content.Context
import android.provider.BaseColumns
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.github.reline.sqlite.db.CopyFromAssetPath
import com.github.reline.sqlite.db.SQLiteCopyOpenHelper
import net.sqlcipher.database.SupportFactory

object FeedReaderContract {
    // Table contents are grouped together in an anonymous object.
    object FeedEntry : BaseColumns {
        const val TABLE_NAME = "entry"
        const val COLUMN_NAME_TITLE = "title"
        const val COLUMN_NAME_SUBTITLE = "subtitle"
    }
}

private const val SQL_CREATE_ENTRIES =
    "CREATE TABLE ${FeedReaderContract.FeedEntry.TABLE_NAME} (" +
            "${BaseColumns._ID} INTEGER PRIMARY KEY," +
            "${FeedReaderContract.FeedEntry.COLUMN_NAME_TITLE} TEXT," +
            "${FeedReaderContract.FeedEntry.COLUMN_NAME_SUBTITLE} TEXT)"

private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS ${FeedReaderContract.FeedEntry.TABLE_NAME}"

fun provideDao(database: SupportSQLiteDatabase): FeedDao {
    return FeedDao(database)
}

fun provideDatabase(factory: SupportSQLiteOpenHelper.Factory, config: SupportSQLiteOpenHelper.Configuration): SupportSQLiteDatabase {
    return factory.create(config).writableDatabase
}

fun provideFeedReaderConfig(context: Context): SupportSQLiteOpenHelper.Configuration {
    return SupportSQLiteOpenHelper.Configuration.builder(context)
        .callback(FeedReaderDbHelperCallback(FeedReaderDbHelperCallback.DATABASE_VERSION))
        .name(FeedReaderDbHelperCallback.DATABASE_NAME)
        .build()
}

fun provideFactory(): SupportSQLiteOpenHelper.Factory {
    return provideCipherFactory()
    // or use the built-in android framework factory for an unencrypted database
//    return provideDefaultFactory()
}

fun provideDefaultFactory(): SupportSQLiteOpenHelper.Factory {
    return FrameworkSQLiteOpenHelperFactory()
}

fun provideCipherFactory(): SupportFactory {
    return SupportFactory("password".toByteArray())
}

fun provideCopyFactory(context: Context, factory: SupportSQLiteOpenHelper.Factory): SQLiteCopyOpenHelper.Factory {
    return SQLiteCopyOpenHelper.Factory(
        context,
        CopyFromAssetPath("FeedReader.db"),
        factory
    )
}

class FeedReaderDbHelperCallback(version: Int) : SupportSQLiteOpenHelper.Callback(version) {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }
    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }
    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    companion object {
        // If you change the database schema, you must increment the database version.
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "FeedReader.db"
    }
}