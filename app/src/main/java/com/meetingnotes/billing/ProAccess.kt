package com.meetingnotes.billing

/**
 * サブスクリプション(Pro)の利用可否を1か所で判定する。
 *
 * Google Play Billing が未実装のため、現状は両フラグとも false 固定。
 * Billing + paywall を実装したら:
 *  - [gatingEnabled] を true に(= 有料機能をロック表示にする)
 *  - [isPro] を実際の購入状態(Flow など)に差し替える
 */
object ProAccess {

    /** 有料機能のロック表示を有効にするか。Billing + paywall が揃うまで false。 */
    var gatingEnabled: Boolean = false
        private set

    /** Pro 加入済みか。Billing 実装時に購入判定へ差し替える。 */
    var isPro: Boolean = false
        private set

    /** UI をロック表示にすべきか(未加入 かつ ゲーティング有効)。 */
    val shouldLock: Boolean
        get() = gatingEnabled && !isPro
}
