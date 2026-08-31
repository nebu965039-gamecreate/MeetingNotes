package com.meetingnotes.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.meetingnotes.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "RewardedAdController"

/**
 * 無料枠クレジット獲得用のリワード広告(仕様書7.1)。
 * 広告ユニットIDは BuildConfig 経由(release=本番 / debug=Google公式テストID)。
 */
class RewardedAdController(private val appContext: Context) {

    private var rewardedAd: RewardedAd? = null

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    fun load() {
        RewardedAd.load(
            appContext,
            BuildConfig.ADMOB_REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    _isLoaded.value = true
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewardedAd = null
                            _isLoaded.value = false
                            load()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.d(TAG, "Rewarded ad failed to show: ${adError.message}")
                            rewardedAd = null
                            _isLoaded.value = false
                        }
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Rewarded ad failed to load: ${adError.message}")
                    rewardedAd = null
                    _isLoaded.value = false
                }
            }
        )
    }

    fun show(activity: Activity, onRewardEarned: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            Log.d(TAG, "Rewarded ad requested but not loaded yet")
            return
        }
        ad.show(activity) { onRewardEarned() }
    }
}
