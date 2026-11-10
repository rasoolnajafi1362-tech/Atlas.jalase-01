package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database holder for Atlas Enterprise Meeting OS.
 */
@Database(
    entities = [
        AttendeeEntity::class,
        MeetingMinutesEntity::class,
        MeetingMinutesFtsEntity::class,
        ExecutiveSummaryEntity::class,
        MemberTaskEntity::class,
        BoardScheduleEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AtlasDatabase : RoomDatabase() {

    abstract fun attendeeDao(): AttendeeDao
    abstract fun meetingMinutesDao(): MeetingMinutesDao
    abstract fun executiveSummaryDao(): ExecutiveSummaryDao
    abstract fun memberTaskDao(): MemberTaskDao
    abstract fun boardScheduleDao(): BoardScheduleDao

    companion object {
        private const val DATABASE_NAME = "atlas_meeting_os.db"

        @Volatile
        private var INSTANCE: AtlasDatabase? = null

        fun getInstance(context: Context): AtlasDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AtlasDatabase::class.java,
                    DATABASE_NAME
                )
                // [Atlas-01 v1.1.0] مایگریشن مخرب حذف شد؛ تغییر اسکیما باید با Migration واقعی همراه باشد.
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Creates an in-memory database instance suitable for fast JVM/Robolectric unit tests.
         */
        fun createInMemory(context: Context): AtlasDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AtlasDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
