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

private const val TAG = "InterstitialAdController"
private const val PREFS_NAME = "ads_prefs"
private const val KEY_LAST_SHOWN_AT = "last_interstitial_shown_at"

/**
 * 要約完了時に表示するインタースティシャル広告(仕様書7.3)。
 * 頻度キャップ(既定1時間)を超えて連続表示しない。未ロードの場合は無音でスキップする。
 * TEST_AD_UNIT_ID はGoogle公式のテスト広告ユニットID。本番配信前に実際のユニットIDへ差し替える。
 */
class InterstitialAdController(
    private val appContext: Context,
    private val frequencyCapMillis: Long = 60 * 60 * 1000L
) {
    private var interstitialAd: InterstitialAd? = null
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load() {
        InterstitialAd.load(
            appContext,
            TEST_AD_UNIT_ID,
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

    companion object {
        const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }
}
