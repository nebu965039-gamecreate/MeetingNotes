package com.meetingnotes

import android.app.Application
import androidx.room.Room
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.MeetingNotesDatabase
import com.meetingnotes.data.local.databaseMigrations
import com.meetingnotes.data.remote.IntegrityTokenProvider

class MeetingNotesApp : Application() {

    val database: MeetingNotesDatabase by lazy {
        Room.databaseBuilder(this, MeetingNotesDatabase::class.java, "meeting-notes.db")
            // クローズドテスト配信は version 5。実利用者は全員 v5 以降なので、
            // v5 以降は正式な Migration を必須にする(未提供ならクラッシュ=書き忘れ防止)。
            // v1〜v4 は開発中・旧テストビルドのみなので破壊的マイグレーションで許容する。
            .addMigrations(*databaseMigrations)
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)
            .build()
    }

    val integrityTokenProvider: IntegrityTokenProvider by lazy {
        IntegrityTokenProvider(this)
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
