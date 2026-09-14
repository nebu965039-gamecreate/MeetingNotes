package com.meetingnotes.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.meetingnotes.BuildConfig

private const val TAG = "AppOpenAdController"
private const val PREFS_NAME = "ads_prefs"
private const val KEY_LAST_SHOWN_AT = "last_app_open_shown_at"

/** Google公式の推奨に合わせ、読み込み済み広告は4時間で失効とみなし再読み込みする。 */
private const val AD_EXPIRATION_MILLIS = 4 * 60 * 60 * 1000L

/**
 * アプリがバックグラウンドから復帰したときに表示するApp Openアド(2026-09-21〜)。
 *
 * 呼び出し側(`MeetingNotesApp`)が以下を保証してから [tryShow] を呼ぶこと:
 *   - 初回起動(コールドスタート)時には呼ばない(起動直後に広告で覆うのは体験が悪い)
 *   - 録音画面が表示中(録音・カウントダウン・文字起こし中など)は呼ばない
 *     (`RecordingScreenGuard.isActive` を見る。録音中に広告へ差し替わるのは危険)
 * これらのガード自体はこのクラスの責務外(単一責任: 広告のロード・表示・頻度制御のみ)。
 */
class AppOpenAdController(private val appContext: Context) {
    private var appOpenAd: AppOpenAd? = null
    private var loadedAtMillis: Long = 0L
    private var isLoading = false
    private var isShowing = false
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load() {
        if (isLoading || isAdAvailable()) return
        isLoading = true
        AppOpenAd.load(
            appContext,
            BuildConfig.ADMOB_APP_OPEN_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    loadedAtMillis = System.currentTimeMillis()
                    isLoading = false
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "App open ad failed to load: ${adError.message}")
                    isLoading = false
                }
            }
        )
    }

    private fun isAdAvailable(): Boolean =
        appOpenAd != null && (System.currentTimeMillis() - loadedAtMillis) < AD_EXPIRATION_MILLIS

    /**
     * ロード済み・表示中でない・頻度キャップ(既定1時間。復帰のたびに出ると鬱陶しいため)を
     * 超えている場合のみ表示する。それ以外は無音でスキップし、次回に備えて再ロードする。
     */
    fun tryShow(activity: Activity, frequencyCapMillis: Long = 60 * 60 * 1000L) {
        if (isShowing) return
        val now = System.currentTimeMillis()
        val lastShownAt = prefs.getLong(KEY_LAST_SHOWN_AT, 0L)
        if (now - lastShownAt < frequencyCapMillis) {
            Log.d(TAG, "App open ad skipped due to frequency cap")
            return
        }

        val ad = appOpenAd
        if (ad == null || !isAdAvailable()) {
            Log.d(TAG, "App open ad requested but not available yet")
            load()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowing = false
                appOpenAd = null
                load()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.d(TAG, "App open ad failed to show: ${adError.message}")
                isShowing = false
                appOpenAd = null
                load()
            }

            override fun onAdShowedFullScreenContent() {
                isShowing = true
            }
        }
        prefs.edit().putLong(KEY_LAST_SHOWN_AT, now).apply()
        ad.show(activity)
    }
}
