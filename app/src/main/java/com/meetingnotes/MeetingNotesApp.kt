package com.meetingnotes

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.room.Room
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.RecordingDraftStore
import com.meetingnotes.data.local.MeetingNotesDatabase
import com.meetingnotes.data.local.databaseMigrations
import com.meetingnotes.data.remote.IntegrityTokenProvider
import com.meetingnotes.notifications.NotificationHelper
import com.meetingnotes.notifications.NotificationSeenState
import com.meetingnotes.notifications.ReminderScheduler
import com.meetingnotes.speech.restoreLeftoverMediaVolume
import com.meetingnotes.util.DiagnosticsLog
import com.meetingnotes.ui.theme.ThemeMode
import com.meetingnotes.ui.theme.ThemePrefs

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

    val recordingDraftStore: RecordingDraftStore by lazy {
        RecordingDraftStore(this)
    }

    private val themePrefs: ThemePrefs by lazy { ThemePrefs(this) }

    /** アプリ全体のテーマ(ライト/ダーク/端末設定)。Compose の状態として持ちどの画面からでも即時反映する。 */
    val themeModeState: MutableState<ThemeMode> by lazy { mutableStateOf(themePrefs.mode) }

    fun setThemeMode(mode: ThemeMode) {
        themePrefs.mode = mode
        themeModeState.value = mode
    }

    val repository: MeetingRepository by lazy {
        MeetingRepository(
            database.clientDao(),
            database.meetingDao(),
            database.todoDao(),
            database.userCreditsDao(),
            database.folderDao(),
            database.clientGroupDao(),
            database.clientBriefingDao(),
            database.notificationLogDao(),
            database.clientContactDao()
        )
    }

    override fun onCreate() {
        super.onCreate()

        DiagnosticsLog.init(this)

        // 前回の録音中にプロセスが落ちてメディア音量が 0 のままなら戻す。
        restoreLeftoverMediaVolume(this)

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

        // F7: 次回打ち合わせのリマインドチェック(周期ジョブ)。
        NotificationHelper.ensureChannel(this)
        ReminderScheduler.schedule(this)
        NotificationSeenState.init(this)
    }
}
