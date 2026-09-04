package com.meetingnotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ClientEntity::class,
        MeetingEntity::class,
        TodoEntity::class,
        UserCreditsEntity::class,
        FolderEntity::class,
        ClientGroupEntity::class,
        ClientBriefingEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MeetingNotesDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun meetingDao(): MeetingDao
    abstract fun todoDao(): TodoDao
    abstract fun userCreditsDao(): UserCreditsDao
    abstract fun folderDao(): FolderDao
    abstract fun clientGroupDao(): ClientGroupDao
    abstract fun clientBriefingDao(): ClientBriefingDao
}
