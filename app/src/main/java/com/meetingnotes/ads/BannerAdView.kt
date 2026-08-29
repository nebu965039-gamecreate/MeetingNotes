package com.meetingnotes.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * 無料ユーザー向けバナー広告(仕様書7章)。ホーム画面(クライアント一覧)・要約結果画面に配置し、
 * 録音中・要約中の画面には表示しない。
 * TEST_AD_UNIT_ID はGoogle公式のテスト広告ユニットID。本番配信前に実際のユニットIDへ差し替える。
 */
@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = TEST_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() }
    )
}

private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
