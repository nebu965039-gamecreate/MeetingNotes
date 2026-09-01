package com.meetingnotes.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.meetingnotes.BuildConfig

private const val TAG = "InterstitialAdController"
private const val PREFS_NAME = "ads_prefs"
private const val KEY_LAST_SHOWN_AT = "last_interstitial_shown_at"

/**
 * 要約の待ち時間に表示するインタースティシャル広告(仕様書7.3)。
 * `submitForSummary` で要約リクエスト直後に `tryShow` を呼び、広告表示中に裏で要約が進む。
 * 頻度キャップ(既定90秒。再試行時の二重表示防止用)内・未ロードのときは無音でスキップ。
 * 広告ユニットIDは BuildConfig 経由(release=本番 / debug=Google公式テストID)。
 */
class InterstitialAdController(
    private val appContext: Context,
    // 要約のたびに1回表示したいので短め。再試行時の二重表示だけを防ぐ。
    private val frequencyCapMillis: Long = 90 * 1000L
) {
    private var interstitialAd: InterstitialAd? = null
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load() {
        InterstitialAd.load(
            appContext,
            BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            load()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.d(TAG, "Interstitial ad failed to show: ${adError.message}")
                            interstitialAd = null
                        }
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Interstitial ad failed to load: ${adError.message}")
                    interstitialAd = null
                }
            }
        )
    }

    /** 頻度キャップ内・広告ロード済みの場合のみ表示する。それ以外は何もしない。 */
    fun tryShow(activity: Activity) {
        val now = System.currentTimeMillis()
        val lastShownAt = prefs.getLong(KEY_LAST_SHOWN_AT, 0L)
        if (now - lastShownAt < frequencyCapMillis) {
            Log.d(TAG, "Interstitial ad skipped due to frequency cap")
            return
        }

        val ad = interstitialAd ?: run {
            Log.d(TAG, "Interstitial ad requested but not loaded yet")
            return
        }

        ad.show(activity)
        prefs.edit().putLong(KEY_LAST_SHOWN_AT, now).apply()
    }
}
