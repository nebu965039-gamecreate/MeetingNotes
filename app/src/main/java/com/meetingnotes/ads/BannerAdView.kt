package com.meetingnotes.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.meetingnotes.BuildConfig

/**
 * バナー広告の形状。
 *  - [AnchoredAdaptive]: 画面幅いっぱいの帯状バナー。常時表示する画面の下部向け(邪魔になりにくい)
 *  - [MediumRectangle]: 300x250 の大判広告。コンテンツ中に埋め込む形。帯状バナーよりeCPMが高い
 */
sealed interface BannerAdFormat {
    data object AnchoredAdaptive : BannerAdFormat
    data object MediumRectangle : BannerAdFormat
}

/**
 * 無料ユーザー向けバナー広告(仕様書7章)。録音中・要約中の画面には表示しない。
 * 広告ユニットIDは BuildConfig 経由(release=本番 / debug=Google公式テストID)。
 * 同じユニットIDで複数サイズを配信できるため、サイズはこのCompose側だけで出し分ける。
 */
@Composable
fun BannerAdView(modifier: Modifier = Modifier, format: BannerAdFormat = BannerAdFormat.AnchoredAdaptive) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val viewModifier = if (format is BannerAdFormat.MediumRectangle) modifier else modifier.fillMaxWidth()

    AndroidView(
        modifier = viewModifier,
        factory = {
            AdView(context).apply {
                setAdSize(
                    when (format) {
                        // 画面幅に高さを最適化する「アダプティブバナー」。固定サイズの標準バナーより
                        // eCPMが平均15〜20%高いとGoogleが案内している。
                        BannerAdFormat.AnchoredAdaptive ->
                            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
                        // 300x250。帯状バナーよりさらにeCPMが高いが専有面積も大きいため、
                        // ユーザーが読み物として時間を使う画面のコンテンツ内にのみ使う。
                        BannerAdFormat.MediumRectangle -> AdSize.MEDIUM_RECTANGLE
                    }
                )
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() }
    )
}
