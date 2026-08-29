package com.meetingnotes

import android.app.Application
import androidx.room.Room
import com.google.android.gms.ads.MobileAds
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.MeetingNotesDatabase

class MeetingNotesApp : Application() {

    val database: MeetingNotesDatabase by lazy {
        Room.databaseBuilder(this, MeetingNotesDatabase::class.java, "meeting-notes.db")
            .fallbackToDestructiveMigration(true)
            .build()
    }

    val repository: MeetingRepository by lazy {
        MeetingRepository(
            database.clientDao(),
            database.meetingDao(),
            database.todoDao(),
            database.userCreditsDao(),
            database.folderDao(),
            database.clientGroupDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
    }
}
