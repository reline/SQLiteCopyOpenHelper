/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.reline.sqlite.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import okio.IOException
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.Callable

/**
 * An open helper that will copy & open a pre-populated database if it doesn't exist in internal
 * storage. Only destructive migrations are supported, so it is highly suggested to use this as a
 * read-only database.
 *
 * https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-master-release/room/runtime/src/main/java/androidx/room/SQLiteCopyOpenHelper.java
 */
class SQLiteCopyOpenHelper(
    private val context: Context,
    private val copyConfig: CopyConfig,
    private val databaseVersion: Int,
    private val delegate: SupportSQLiteOpenHelper,
    private val databaseFileDelegate: DatabaseFileDelegate = DatabaseFileDelegate(context),
) : SupportSQLiteOpenHelper {

    private var verified = false

    override fun getDatabaseName(): String? {
        return delegate.databaseName
    }

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        delegate.setWriteAheadLoggingEnabled(enabled)
    }

    @Synchronized
    override fun getWritableDatabase(): SupportSQLiteDatabase {
        if (!verified) {
            verifyDatabaseFile()
            verified = true
        }
        return delegate.writableDatabase
    }

    @Synchronized
    override fun getReadableDatabase(): SupportSQLiteDatabase {
        if (!verified) {
            verifyDatabaseFile()
            verified = true
        }
        return delegate.readableDatabase
    }

    @Synchronized
    override fun close() {
        delegate.close()
        verified = false
    }

    private fun verifyDatabaseFile() {
        val databaseName = databaseName
        val databaseFile = databaseFileDelegate.getDatabaseFile(databaseName)
        val lockChannel =
            FileOutputStream(File(context.filesDir, "$databaseName.lck")).channel
        try {
            // Acquire a file lock, this lock works across threads and processes, preventing
            // concurrent copy attempts from occurring.
            lockChannel.tryLock()

            if (!databaseFile.exists()) {
                try {
                    // No database file found, copy and be done.
                    copyDatabaseFile(databaseFile)
                    return
                } catch (e: IOException) {
                    throw RuntimeException("Unable to copy database file.", e)
                }
            }

            // A database file is present, check if we need to re-copy it.
            val currentVersion = try {
                readVersion(databaseFile)
            } catch (e: IOException) {
                Log.w(TAG, "Unable to read database version.", e)
                return
            }

            if (currentVersion == databaseVersion) {
                return
            }

            // Always overwrite, we don't support migrations
            if (SQLiteDatabase.deleteDatabase(databaseFileDelegate.getDatabaseFile(databaseName))) {
                try {
                    copyDatabaseFile(databaseFile)
                } catch (e: IOException) {
                    // We are more forgiving copying a database on a destructive migration since
                    // there is already a database file that can be opened.
                    Log.w(TAG, "Unable to copy database file.", e)
                }
            } else {
                Log.w(
                    TAG,
                    "Failed to delete database file ($databaseName) for a copy destructive migration."
                )
            }
        } finally {
            try {
                lockChannel.close()
            } catch (ignored: IOException) {
            }
        }
    }

    /**
     * Reads the user version number out of the database header from the given file.
     *
     * @param databaseFile the database file.
     * @return the database version
     * @throws IOException if something goes wrong reading the file, such as bad database header or
     * missing permissions.
     *
     * @see <a href="https://www.sqlite.org/fileformat.html#user_version_number">User Version
     * Number</a>.
     */
    @Throws(IOException::class)
    private fun readVersion(databaseFile: File): Int {
        return FileInputStream(databaseFile).channel.use { input ->
            input.tryLock(60, 4, true)
            input.position(60)
            val buffer = ByteBuffer.allocate(4)
            val read = input.read(buffer)
            if (read != 4) {
                throw IOException("Bad database header, unable to read 4 bytes at offset 60")
            }
            buffer.rewind()
            buffer.int // ByteBuffer is big-endian by default
        }
    }

    @Throws(IOException::class)
    private fun copyDatabaseFile(destinationFile: File) {
        val input = when (copyConfig) {
            is CopyFromAssetPath -> {
                context.assets.open(copyConfig.path)
            }
            is CopyFromFile -> {
                FileInputStream(copyConfig.file)
            }
            is CopyFromInputStream -> {
                copyConfig.callable.call()
            }
        }

        // An intermediate file is used so that we never end up with a half-copied database file
        // in the internal directory.
        val intermediateFile = File.createTempFile(
            "sqlite-copy-helper", ".tmp", context.cacheDir
        )
        intermediateFile.deleteOnExit()
        input.source().use { a ->
            intermediateFile.sink().buffer().use { b -> b.writeAll(a) }
        }

        val parent = destinationFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("Failed to create directories for ${destinationFile.absolutePath}")
        }

        if (!intermediateFile.renameTo(destinationFile)) {
            throw IOException("Failed to move intermediate file (${intermediateFile.absolutePath}) to destination (${destinationFile.absolutePath}).")
        }
    }

    /**
     * Implementation of {@link SupportSQLiteOpenHelper.Factory} that creates
     * {@link SQLiteCopyOpenHelper}.
     */
    class Factory(
        private val context: Context,
        private val copyConfig: CopyConfig,
        private val delegate: SupportSQLiteOpenHelper.Factory,
    ) : SupportSQLiteOpenHelper.Factory {
        override fun create(config: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            return SQLiteCopyOpenHelper(
                context,
                copyConfig,
                config.callback.version,
                delegate.create(config),
                DatabaseFileDelegate(context, config.useNoBackupDirectory),
            )
        }
    }

    companion object {
        private const val TAG = "SQLiteCopyOpenHelper"
    }
}

class DatabaseFileDelegate(
    private val context: Context,
    private val useNoBackupDirectory: Boolean = false,
) {

    fun getDatabaseFile(databaseName: String?): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && databaseName != null && useNoBackupDirectory) {
            File(context.noBackupFilesDir, databaseName)
        } else {
            context.getDatabasePath(databaseName)
        }
    }
}

sealed class CopyConfig
data class CopyFromAssetPath(val path: String) : CopyConfig()
data class CopyFromFile(val file: File) : CopyConfig()
data class CopyFromInputStream(val callable: Callable<InputStream>) : CopyConfig()
