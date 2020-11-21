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

### Copying from Assets folder

Copy your database file to the assets folder.
```
src/main/java/...
src/main/assets/database.sqlite
```

Then use the `CopyFromAssetPath` configuration to specify the path to your database.
```kotlin
val factory: SupportSQLiteOpenHelper.Factory = SQLiteCopyOpenHelper.Factory(
        context = applicationContext,
        delegate = FrameworkSQLiteOpenHelperFactory(),
        copyConfig = CopyFromAssetPath("database.sqlite")
)
```

### Copying from other sources

```kotlin
copyConfig = CopyFromFile(file = File(...))
...
copyConfig = CopyFromInputStream { inputStream }
```

### SQLDelight

The driver for SQLDelight accepts a SupportSQLiteOpenHelper.Factory, so simply pass in the SQLiteCopyOpenHelper.Factory.
```kotlin
val factory: SupportSQLiteOpenHelper.Factory = SQLiteCopyOpenHelper.Factory(...)
val driver: SqlDriver = AndroidSqliteDriver(
        context = applicationContext,
        schema = Database.Schema,
        factory = factory,
        name = "database.db"
)
val database = Database(driver)
```

## Installation

```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.reline:sqlitecopyopenhelper:0.1.0'
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
