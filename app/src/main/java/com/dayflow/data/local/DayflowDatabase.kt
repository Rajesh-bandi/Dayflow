package com.dayflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, TaskOccurrenceEntity::class, FocusSessionEntity::class, RoutineEntity::class, FocusProfileEntity::class, AiMessageEntity::class, AiMemoryEntity::class, AiUserProfileEntity::class, TaskRescheduleEntity::class],
    version = 8,
    exportSchema = false
)
abstract class DayflowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun occurrenceDao(): TaskOccurrenceDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun routineDao(): RoutineDao
    abstract fun focusProfileDao(): FocusProfileDao
    abstract fun aiDao(): AiDao

    companion object {
        @Volatile
        private var instance: DayflowDatabase? = null

        fun get(context: Context): DayflowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DayflowDatabase::class.java,
                    "dayflow.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            db.execSQL("INSERT OR IGNORE INTO focus_profiles (name, focusMinutes, breakMinutes, createdAtMillis) VALUES ('Pomodoro', 25, 5, 0), ('Deep work', 50, 10, 0), ('Flow', 90, 20, 0)")
                        }
                    }).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS focus_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER, profileName TEXT NOT NULL, startedAtMillis INTEGER NOT NULL, expectedEndAtMillis INTEGER NOT NULL, pausedAtMillis INTEGER, accumulatedPauseMillis INTEGER NOT NULL, endedAtMillis INTEGER, actualDurationMillis INTEGER, status TEXT NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_startedAtMillis ON focus_sessions(startedAtMillis)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_status ON focus_sessions(status)")
            }
        }

        /** Removes only rows inserted by the old first-launch demo seeder (zero timestamps + known titles). */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                val seededTitles = "'DSA Practice - Graph Algorithms','Morning Mobility & Core','Get Ready & Breakfast','Spring Boot Microservices','AWS Builder Center Daily Streak','DevOps & Docker Learning','Evening Reading & Journal'"
                database.execSQL("DELETE FROM task_occurrences WHERE taskId IN (SELECT id FROM tasks WHERE createdAtMillis = 0 AND title IN ($seededTitles))")
                database.execSQL("DELETE FROM tasks WHERE createdAtMillis = 0 AND title IN ($seededTitles)")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS routines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, type TEXT NOT NULL, startTimeMinutes INTEGER NOT NULL, durationMinutes INTEGER NOT NULL, priority TEXT NOT NULL, destinationUrl TEXT, focusProfile TEXT, createdAtMillis INTEGER NOT NULL)")
            }
        }
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS focus_profiles (name TEXT NOT NULL PRIMARY KEY, focusMinutes INTEGER NOT NULL, breakMinutes INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL)")
                database.execSQL("INSERT OR IGNORE INTO focus_profiles (name, focusMinutes, breakMinutes, createdAtMillis) VALUES ('Pomodoro', 25, 5, 0), ('Deep work', 50, 10, 0), ('Flow', 90, 20, 0)")
            }
        }
        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE task_occurrences ADD COLUMN status TEXT NOT NULL DEFAULT 'PLANNED'")
                database.execSQL("UPDATE task_occurrences SET status = CASE WHEN completed = 1 THEN 'COMPLETED' ELSE 'PLANNED' END")
            }
        }
        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN archivedAtMillis INTEGER")
            }
        }
        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS ai_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mode TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, createdAtMillis INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_messages_mode_createdAtMillis ON ai_messages(mode, createdAtMillis)")
                database.execSQL("CREATE TABLE IF NOT EXISTS ai_memories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, category TEXT NOT NULL, content TEXT NOT NULL, explicit INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_memories_category ON ai_memories(category)")
                database.execSQL("CREATE TABLE IF NOT EXISTS ai_user_profile (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, wakeTime TEXT NOT NULL, windDownTime TEXT NOT NULL, preferences TEXT NOT NULL, goals TEXT NOT NULL, updatedAtMillis INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS task_reschedules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, newTaskId INTEGER NOT NULL, fromEpochDay INTEGER NOT NULL, toEpochDay INTEGER NOT NULL, createdAtMillis INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_task_reschedules_taskId_fromEpochDay ON task_reschedules(taskId, fromEpochDay)")
            }
        }
    }
}
