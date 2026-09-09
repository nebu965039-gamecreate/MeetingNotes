package com.meetingnotes.billing

import com.meetingnotes.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * サブスクリプション(Pro)の利用可否を1か所で判定する。
 *
 * - [gatingEnabled] はビルド時フラグ `BuildConfig.PRO_GATING_ENABLED`(既定 false)。
 *   Play Console のサブスク商品登録・ライセンステストが済むまで false のままにする。
 * - [isPro] / [isProFlow] は `BillingManager` の購入状態(`MeetingNotesApp` が [setPro] で流し込む)。
 * - UI をロック表示にすべきかは [shouldLock](= ゲーティング有効 かつ 未加入)。
 *   Compose で購入状態の変化に追従したい画面は [isProFlow] を `collectAsState()` して
 *   `ProAccess.gatingEnabled && !isPro` を自前で計算する。
 */
object ProAccess {

    private val _isPro = MutableStateFlow(false)
    val isProFlow: StateFlow<Boolean> = _isPro.asStateFlow()

    /** BillingManager から購入状態を反映する(`MeetingNotesApp` から呼ぶ)。 */
    fun setPro(pro: Boolean) {
        _isPro.value = pro
    }

    /** 有料機能のロック表示を有効にするか。 */
    val gatingEnabled: Boolean
        get() = BuildConfig.PRO_GATING_ENABLED

    /** Pro 加入済みか(スナップショット)。 */
    val isPro: Boolean
        get() = _isPro.value

    /** UI をロック表示にすべきか(未加入 かつ ゲーティング有効)。 */
    val shouldLock: Boolean
        get() = gatingEnabled && !_isPro.value
}
