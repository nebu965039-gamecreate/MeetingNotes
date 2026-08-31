package com.meetingnotes

import android.app.Application
import androidx.room.Room
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
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

        // 実機テスターにテスト広告を配信する端末を登録(エミュレータは登録不要)。
        // release ビルドでも本番広告に実トラフィックを出さずに動作確認できる。
        val testDeviceIds = BuildConfig.ADMOB_TEST_DEVICE_IDS
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (testDeviceIds.isNotEmpty()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
            )
        }

        MobileAds.initialize(this) {}
    }
}
