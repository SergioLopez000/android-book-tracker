package com.librosleidos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for the app (design: Migration / Rollout — schema v1,
 * exported via KSP `room.schemaLocation` to `app/schemas/` so future
 * migrations stay diffable; no `fallbackToDestructiveMigration`).
 */
@Database(entities = [BookEntity::class], version = 1, exportSchema = true)
abstract class BooksDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
