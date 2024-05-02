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
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import okio.FileSystem
import okio.IOException
import okio.Lock
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.assetfilesystem.asFileSystem
import okio.source
import okio.withLock
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.StandardOpenOption
import java.util.concurrent.Callable
import kotlin.jvm.Throws

/**
 * An open helper that will copy & open a pre-populated database if it doesn't exist in internal
 * storage.
 *
 * Only destructive migrations are supported, so it is highly suggested to use this as a
 * read-only database.
 *
 * @see <a href="https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-master-release/room/runtime/src/main/java/androidx/room/SQLiteCopyOpenHelper.java">androidx.room.SQLiteCopyOpenHelper</a>
 */
class SQLiteCopyOpenHelper internal constructor(
    private val copyConfig: CopyConfig,
    private val databaseVersion: Int,
    private val delegate: SupportSQLiteOpenHelper,
    private val databasePath: Path,
    private val files: Path,
    private val cache: Path,
    private val assets: FileSystem,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : SupportSQLiteOpenHelper by delegate {

    private val threadLock = Lock()
    private var verified = false

    constructor(
        context: Context,
        copyConfig: CopyConfig,
        databaseVersion: Int,
        delegate: SupportSQLiteOpenHelper,
    ) : this(
        copyConfig,
        databaseVersion,
        delegate,
        databasePath = context.getDatabasePath(delegate.databaseName).toOkioPath(),
        files = context.filesDir.toOkioPath(),
        cache = context.cacheDir.toOkioPath(),
        assets = context.assets.asFileSystem(),
    )

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

    private fun verifyDatabaseFile() = withLock {
        if (!fileSystem.exists(databasePath)) {
            try {
                // No database file found, copy and be done.
                copyDatabaseFile(databasePath)
                return@withLock
            } catch (e: IOException) {
                throw RuntimeException("Unable to copy database file.", e)
            }
        }

        // A database file is present, check if we need to re-copy it.
        val currentVersion = try {
            readVersion(databasePath)
        } catch (e: IOException) {
            Timber.w(e, "Unable to read database version.")
            return@withLock
        }

        if (currentVersion == databaseVersion) {
            return@withLock
        }

        // todo: support migrations
        //  if (isMigrationRequired(currentVersion, databaseVersion) return@withLock

        // Always overwrite, we don't support migrations
        try {
            fileSystem.delete(databasePath)
            try {
                copyDatabaseFile(databasePath)
            } catch (e: IOException) {
                // We are more forgiving copying a database on a destructive migration since
                // there is already a database file that can be opened.
                Timber.w(e, "Unable to copy database file.")
            }
        } catch (e: IOException) {
            Timber.w(e, "Failed to delete database file ($databaseName) for a copy destructive migration.")
        }
    }

    /**
     * Acquire a lock, this lock works across threads and processes, preventing concurrent copy
     * attempts from occurring.
     */
    private fun withLock(action: () -> Unit) = threadLock.withLock {
        val lockFile = files/"$databaseName.lck"
        lockFile.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.write(lockFile) {}
        lockFile.withLock(action)
    }

    @Throws(IOException::class)
    private fun copyDatabaseFile(destination: Path) {
        val input = when (copyConfig) {
            is CopyFromAssetPath -> {
                assets.source(copyConfig.path.toPath())
            }
            is CopyFromFile -> {
                fileSystem.source(copyConfig.file.toOkioPath())
            }
            is CopyFromInputStream -> try {
                copyConfig.callable.call().source()
            } catch (e: Exception) {
                throw IOException("Failed to copy database from input stream", e)
            }
        }

        // An intermediate file is used so that we never end up with a half-copied database file
        // in the internal directory.
        val intermediateFile = cache/"sqlite-copy-helper.tmp"
        intermediateFile.toFile().deleteOnExit()
        fileSystem.write(intermediateFile, mustCreate = true) { writeAll(input) }
        destination.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.atomicMove(intermediateFile, destination)
        fileSystem.delete(intermediateFile)
    }

    /**
     * Implementation of [SupportSQLiteOpenHelper.Factory] that creates [SQLiteCopyOpenHelper].
     */
    class Factory(
        private val context: Context,
        private val copyConfig: CopyConfig,
        private val delegate: SupportSQLiteOpenHelper.Factory
    ) : SupportSQLiteOpenHelper.Factory {
        override fun create(config: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            return SQLiteCopyOpenHelper(
                context,
                copyConfig,
                config.callback.version,
                delegate.create(config)
            )
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
internal fun readVersion(databaseFile: Path): Int {
    return FileInputStream(databaseFile.toFile()).channel.use { input ->
        val buffer = ByteBuffer.allocate(4)
        input.tryLock(60, 4, true)
        input.position(60)
        val read = input.read(buffer)
        if (read != 4) {
            throw IOException("Bad database header, unable to read 4 bytes at offset 60")
        }
        buffer.rewind()
        buffer.int // ByteBuffer is big-endian by default
    }
}

internal fun <T> Path.withLock(action: () -> T): T {
    // fixme: real file api attempts to find path in fake file system
    Timber.d("attempting file lock: $this")
    val channel = toFile().outputStream().channel
    channel.lock()
    channel.use {
        return action()
    }
}

sealed class CopyConfig
data class CopyFromAssetPath(val path: String): CopyConfig()
data class CopyFromFile(val file: File): CopyConfig()
data class CopyFromInputStream(val callable: Callable<InputStream>): CopyConfig()
