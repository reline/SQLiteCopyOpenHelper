# SQLiteCopyOpenHelper
An open helper for Android that will copy &amp; open a pre-populated database

## Room

If you are using Room, utilize the built in methods for prepopulating a database.
https://developer.android.com/training/data-storage/room/prepopulate

## Usage

Add the Android SQLite framework as a dependency for a default factory implementation.

```groovy
dependencies {
    implementation "androidx.sqlite:sqlite-framework:$sqlite_version"
}
```

### Prepopulate from an app asset

Copy your database file to the assets folder.
```
src/main/java/...
src/main/assets/database.sqlite
```

Then use the `CopySource.FromAssetPath` configuration to specify the path to your database.
```kotlin
val source = CopySource.FromAssetPath("database.sqlite")
val migrationStrategy = ..
val factory: SupportSQLiteOpenHelper.Factory = SQLiteCopyOpenHelper.Factory(
        delegate = FrameworkSQLiteOpenHelperFactory(),
        copyConfig = CopyConfig(source, migrationStrategy),
)
```

### Prepopulate from other sources

```kotlin
val source = CopySource.FromFile(File(..))
...
val source = CopySource.FromInputStream { inputStream }
```

### Handle migrations that include prepackaged databases

The default migration strategy is `Destructive` for prepackaged databases, meaning when the database
version on the device does not match the latest schema version, the database tables will be
recreated.

If valid migrations are necessary between versions then the `Required` strategy can also be used, or
`DestructiveOnDowngrade` if the database does not support downgrades.

Destructive migrations can be enabled for only a set of specific starting schema versions as well.
```kt
val migrationStrategy = MigrationStrategy.DestructiveFrom(setOf(
    1, 2, 3, // the first migration only supports version 4 -> 5 
))
```

### SQLDelight

The `AndroidSqliteDriver` for SQLDelight accepts a `SupportSQLiteOpenHelper.Factory`, so simply pass
in a `SQLiteCopyOpenHelper.Factory`.
```kotlin
val factory: SupportSQLiteOpenHelper.Factory = SQLiteCopyOpenHelper.Factory(..)
val driver: SqlDriver = AndroidSqliteDriver(
        context = applicationContext,
        schema = Database.Schema,
        factory = factory,
        name = "database.db",
)
val database = Database(driver)
```

## Installation

```groovy
allprojects {
    repositories {
        mavenCentral()
    }
}

dependencies {
    implementation 'io.github.reline:sqlitecopyopenhelper:0.1.0'
}
```

License
--------

    Copyright 2020 Nathan Reline

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
