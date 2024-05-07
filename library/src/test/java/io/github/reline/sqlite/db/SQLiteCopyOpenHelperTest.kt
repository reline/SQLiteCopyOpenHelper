package io.github.reline.sqlite.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.transaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SQLiteCopyOpenHelperTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun copyEmptyDatabaseFileFromAssetPath() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .callback(TestCallback(1))
            .name("database.sqlite")
            .build()
        SQLiteCopyOpenHelper.Factory(
            CopyConfig(CopySource.FromAssetPath("EmptyDatabaseFile.sqlite")),
            FrameworkSQLiteOpenHelperFactory()
        )
            .create(config)
            .writableDatabase
            .close()
    }

    @Test
    fun destructivelyMigrateByDefault() {
        val configBuilder = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("database.sqlite")

        SQLiteCopyOpenHelper.Factory(
            CopyConfig(CopySource.FromAssetPath("1.sqlite")),
            FrameworkSQLiteOpenHelperFactory()
        )
            .create(configBuilder.callback(TestCallback(1)).build())
            .readableDatabase.use { db ->
                Assert.assertEquals(1, db.version)
                val value = db.getValue()
                Assert.assertEquals(1, value)
            }

        SQLiteCopyOpenHelper.Factory(
            CopyConfig(CopySource.FromAssetPath("2.sqlite")),
            FrameworkSQLiteOpenHelperFactory()
        )
            .create(configBuilder.callback(TestCallback(2)).build())
            .readableDatabase.use { db ->
                Assert.assertEquals(2, db.version)
                val value = db.getValue()
                Assert.assertEquals(2, value)
            }
    }

    @Test
    fun nonDestructiveMigration() {
        val configBuilder = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("database.sqlite")

        SQLiteCopyOpenHelper.Factory(
            CopyConfig(
                CopySource.FromAssetPath("1.sqlite"),
                MigrationStrategy.Required
            ), FrameworkSQLiteOpenHelperFactory()
        )
            .create(configBuilder.callback(TestCallback(1)).build())
            .readableDatabase.use { db ->
                Assert.assertEquals(1, db.version)
                val value = db.getValue()
                Assert.assertEquals(1, value)
            }

        SQLiteCopyOpenHelper.Factory(
            CopyConfig(
                CopySource.FromAssetPath("2.sqlite"),
                MigrationStrategy.Required
            ), FrameworkSQLiteOpenHelperFactory()
        )
            .create(configBuilder.callback(TestCallback(2)).build())
            .readableDatabase.use { db ->
                Assert.assertEquals(2, db.version)
                val value = db.getValue()
                Assert.assertEquals(1, value)
            }

        val callback = object : TestCallback(3) {
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                db.transaction {
                    db.execSQL("""UPDATE Test SET value = 3;""")
                }
            }
        }
        val copyConfig = CopyConfig(
            CopySource.FromAssetPath("EmptyDatabaseFile.sqlite"),
            MigrationStrategy.Required
        )
        SQLiteCopyOpenHelper.Factory(copyConfig, FrameworkSQLiteOpenHelperFactory())
            .create(configBuilder.callback(callback).build())
            .readableDatabase.use { db ->
                Assert.assertEquals(3, db.version)
                val value = db.getValue()
                Assert.assertEquals(3, value)
            }
    }

    @Test
    fun destructiveOnDowngradeOnly() {
        val configBuilder = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("database.sqlite")

        SQLiteCopyOpenHelper.Factory(
            CopyConfig(
                CopySource.FromAssetPath("2.sqlite"),
                MigrationStrategy.DestructiveOnDowngrade
            ), FrameworkSQLiteOpenHelperFactory()
        )
            .create(configBuilder.callback(TestCallback(2)).build())
            .readableDatabase.use { db ->
                Assert.assertEquals(2, db.version)
                val value = db.getValue()
                Assert.assertEquals(2, value)
            }

        SQLiteCopyOpenHelper.Factory(
            CopyConfig(
                CopySource.FromAssetPath("1.sqlite"),
                MigrationStrategy.DestructiveOnDowngrade
            ), FrameworkSQLiteOpenHelperFactory()
        )
            .create(configBuilder.callback(TestCallback(1)).build())
            .readableDatabase.use { db ->
                Assert.assertEquals(1, db.version)
                val value = db.getValue()
                Assert.assertEquals(1, value)
            }

        val callback = object : TestCallback(3) {
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                db.transaction {
                    db.execSQL("""UPDATE Test SET value = 3;""")
                }
            }
        }
        val copyConfig = CopyConfig(
            CopySource.FromAssetPath("EmptyDatabaseFile.sqlite"),
            MigrationStrategy.DestructiveOnDowngrade
        )
        SQLiteCopyOpenHelper.Factory(copyConfig, FrameworkSQLiteOpenHelperFactory())
            .create(configBuilder.callback(callback).build())
            .readableDatabase.use { db ->
                Assert.assertEquals(3, db.version)
                val value = db.getValue()
                Assert.assertEquals(3, value)
            }
    }

    @Test
    fun destructiveMigrationFromSpecificVersions() {
        val configBuilder = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("database.sqlite")

        val migrationStrategy = MigrationStrategy.DestructiveFrom(setOf(1))

        SQLiteCopyOpenHelper.Factory(
            CopyConfig(
                CopySource.FromAssetPath("1.sqlite"),
                migrationStrategy
            ), FrameworkSQLiteOpenHelperFactory()
        )
            .create(configBuilder.callback(TestCallback(1)).build())
            .readableDatabase.use { db ->
                Assert.assertEquals(1, db.version)
                val value = db.getValue()
                Assert.assertEquals(1, value)
            }

        SQLiteCopyOpenHelper.Factory(
            CopyConfig(
                CopySource.FromAssetPath("2.sqlite"),
                migrationStrategy
            ), FrameworkSQLiteOpenHelperFactory()
        )
            .create(configBuilder.callback(TestCallback(2)).build())
            .readableDatabase.use { db ->
                Assert.assertEquals(2, db.version)
                val value = db.getValue()
                Assert.assertEquals(2, value)
            }

        val callback = object : TestCallback(3) {
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                db.transaction {
                    db.execSQL("""UPDATE Test SET value = 3;""")
                }
            }
        }
        val copyConfig =
            CopyConfig(CopySource.FromAssetPath("EmptyDatabaseFile.sqlite"), migrationStrategy)
        SQLiteCopyOpenHelper.Factory(copyConfig, FrameworkSQLiteOpenHelperFactory())
            .create(configBuilder.callback(callback).build())
            .readableDatabase.use { db ->
                Assert.assertEquals(3, db.version)
                val value = db.getValue()
                Assert.assertEquals(3, value)
            }
    }
}

private fun SupportSQLiteDatabase.getValue(): Int = transaction {
    query("""SELECT value FROM Test LIMIT 1""").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
}

private open class TestCallback(version: Int) : SupportSQLiteOpenHelper.Callback(version) {
    override fun onCreate(db: SupportSQLiteDatabase) {}
    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}
