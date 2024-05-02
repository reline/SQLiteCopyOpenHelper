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

import java.io.File
import java.io.InputStream
import java.util.concurrent.Callable

/**
 * Configuration class for a [SQLiteCopyOpenHelper].
 *
 * For backwards compatibility, the default migration strategy is [MigrationStrategy.Destructive].
 */
class CopyConfig(
    val copySource: CopySource,
    val migrationStrategy: MigrationStrategy = MigrationStrategy.Destructive,
)

/**
 * Source from which to copy a pre-packaged database.
 */
sealed interface CopySource {

    /**
     * Create and open the database using a pre-packaged database located in the application
     * 'assets/' folder.
     *
     * The pre-packaged database is not opened, instead it is copied to the internal app database
     * folder, and is then opened. The pre-packaged database file must be located in the 'assets/'
     * folder of your application. For example, the path for a file located in
     * "assets/databases/products.db" would be "databases/products.db".
     *
     * @param databaseFilePath The file path within the 'assets/' directory of where the database
     *                         file is located.
     */
    data class FromAssetPath(val databaseFilePath: String): CopySource

    /**
     * Create and open the database using a pre-packaged database file.
     *
     * The pre-packaged database is not opened, instead it is copied to the internal app database
     * folder, and is then opened. The given file must be accessible and the right permissions must
     * be granted to copy the file.
     *
     * @param databaseFile The database file.
     */
    data class FromFile(val databaseFile: File): CopySource

    /**
     * Create and open the database using a pre-packaged database via an [InputStream].
     *
     * This is useful for processing compressed database files. The pre-packaged database is not
     * opened, instead it is copied to the internal app database folder, and is then opened. The
     * [InputStream] will be closed once it is done being consumed.
     *
     * @param callable A callable that returns an [InputStream] from which to copy the database. The
     *                 callable is only invoked if the database needs to be created and opened from
     *                 the pre-package database, usually the first time it is created or during a
     *                 destructive migration.
     */
    data class FromInputStream(val callable: Callable<InputStream>): CopySource
}

sealed interface MigrationStrategy {

    /**
     * Require a valid migration if version changes, instead of recreating the tables.
     */
    data object Required : MigrationStrategy {
        override fun isMigrationRequired(fromVersion: Int, toVersion: Int) = true
    }

    /**
     * Destructively recreate database tables when the database version on the device does not match
     * the latest schema version.
     */
    data object Destructive : MigrationStrategy {
        override fun isMigrationRequired(fromVersion: Int, toVersion: Int) = false
    }

    /**
     * Destructively recreate database tables when downgrading to old schema versions.
     */
    data object DestructiveOnDowngrade : MigrationStrategy {
        override fun isMigrationRequired(fromVersion: Int, toVersion: Int): Boolean {
            return fromVersion < toVersion
        }
    }

    /**
     * Destructively recreate database tables from specific starting schema versions.
     *
     * @param startVersions The collection of schema versions from which migrations aren't required.
     */
    data class DestructiveFrom(private val startVersions: Set<Int>) : MigrationStrategy {
        override fun isMigrationRequired(fromVersion: Int, toVersion: Int): Boolean {
            return !startVersions.contains(fromVersion)
        }
    }

    /**
     * Returns whether a migration is required between two versions.
     *
     * @param fromVersion The old schema version.
     * @param toVersion   The new schema version.
     * @return True if a valid migration is required, false otherwise.
     */
    fun isMigrationRequired(fromVersion: Int, toVersion: Int): Boolean
}
