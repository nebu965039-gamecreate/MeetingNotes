package com.meetingnotes

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.meetingnotes.ads.AppOpenAdController
import com.meetingnotes.ads.RecordingScreenGuard
import com.meetingnotes.analytics.AnalyticsPrefs
import com.meetingnotes.analytics.AppAnalytics
import com.meetingnotes.billing.BillingManager
import com.meetingnotes.billing.ProAccess
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
import kotlinx.coroutines.launch

class MeetingNotesApp : Application() {

    /** App Openアド用に、現在フォアグラウンドのActivityを追跡する(単一Activity構成)。 */
    private var currentActivity: Activity? = null

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

    /** Google Play Billing(Pro サブスクリプション)。 */
    val billingManager: BillingManager by lazy { BillingManager(this) }

    /** アプリ復帰時に表示するApp Openアド(2026-09-21〜)。 */
    val appOpenAdController: AppOpenAdController by lazy { AppOpenAdController(this) }

    private val themePrefs: ThemePrefs by lazy { ThemePrefs(this) }

    private val analyticsPrefs: AnalyticsPrefs by lazy { AnalyticsPrefs(this) }

    /** 利用状況データ送信(Analytics/Crashlytics)のON/OFF。設定画面から呼ぶ。 */
    fun setAnalyticsEnabled(enabled: Boolean) {
        analyticsPrefs.enabled = enabled
        AppAnalytics.setCollectionEnabled(enabled)
    }

    fun isAnalyticsEnabled(): Boolean = analyticsPrefs.enabled

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
            database.notificationLogDao(),
            database.clientContactDao(),
            database.clientProjectDao(),
            database.scheduleDao(),
            database.emailTemplateDao()
        )
    }

    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    override fun onCreate() {
        super.onCreate()

        DiagnosticsLog.init(this)

        // Firebase Analytics / Crashlytics(2026-09-23〜)。google-services.json 未配置なら
        // BuildConfig.FIREBASE_ENABLED=false で AppAnalytics 側が no-op になる。
        AppAnalytics.init(this)
        AppAnalytics.setCollectionEnabled(analyticsPrefs.enabled)

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

        // Google Play Billing を起動し、購入状態を ProAccess に流し込む。
        billingManager.start()
        appScope.launch { billingManager.isPro.collect { ProAccess.setPro(it) } }

        // App Openアド(2026-09-21〜): アプリがバックグラウンドから復帰したときに表示する。
        // 現在のActivityを追跡しておき(単一Activity構成)、ProcessLifecycleOwner の onStart で
        // 表示を試みる。初回起動(コールドスタート)と録音画面表示中(RecordingScreenGuard)は
        // スキップする(起動直後・録音中に広告で覆うのは体験が悪いため)。
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) { currentActivity = activity }
            override fun onActivityPaused(activity: Activity) { if (currentActivity === activity) currentActivity = null }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        appOpenAdController.load()
        var isColdStart = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (isColdStart) {
                    // 起動直後の最初の onStart は「これから開く」瞬間なのでスキップする。
                    isColdStart = false
                    return
                }
                if (RecordingScreenGuard.isActive) return
                currentActivity?.let { appOpenAdController.tryShow(it) }
            }
        })

        // F7: 次回打ち合わせのリマインドチェック(周期ジョブ)。
        NotificationHelper.ensureChannel(this)
        ReminderScheduler.schedule(this)
        NotificationSeenState.init(this)

        // 既存の meetings.nextMeetingDate を schedules に取り込む(v17 移行の一度きり。以降は saveMeeting/
        // setNextMeeting が連動して作るので不要)。
        val prefs = getSharedPreferences("app_migrations", MODE_PRIVATE)
        if (!prefs.getBoolean("schedules_backfilled", false)) {
            appScope.launch {
                runCatching { repository.backfillSchedules() }
                    .onSuccess { prefs.edit { putBoolean("schedules_backfilled", true) } }
            }
        }
        // 案件フェーズを正にした移行(2026-09-11): 商談があるのに案件が無いクライアントへ案件を1件作る。
        if (!prefs.getBoolean("client_projects_backfilled", false)) {
            appScope.launch {
                runCatching { repository.backfillClientProjects() }
                    .onSuccess { prefs.edit { putBoolean("client_projects_backfilled", true) } }
            }
        }
    }
}
