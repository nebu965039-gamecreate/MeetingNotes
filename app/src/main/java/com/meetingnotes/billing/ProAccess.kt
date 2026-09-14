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
 * - [debugPreviewLockedFlow] はデバッグビルド限定のプレビュー切替(下記参照)。
 */
object ProAccess {

    private val _isPro = MutableStateFlow(false)
    val isProFlow: StateFlow<Boolean> = _isPro.asStateFlow()

    /**
     * デバッグビルド限定(2026-09-21〜)。設定画面「デバッグ」カードの
     * 「Proロック表示をプレビュー」トグル用。ONの間は実際のビルドフラグ(`PRO_GATING_ENABLED`、
     * 現状 false)や実際の購入状態に関わらず、無料・未加入ユーザーが見る画面(Proバッジ・
     * ペイウォール等)を強制的に表示する。リリースビルドでは [setDebugPreviewLocked] が
     * 無視されるため常に false のまま(=本番挙動に影響しない)。
     */
    private val _debugPreviewLocked = MutableStateFlow(false)
    val debugPreviewLockedFlow: StateFlow<Boolean> = _debugPreviewLocked.asStateFlow()

    /** デバッグビルドでのみ有効(リリースビルドでは無視される)。 */
    fun setDebugPreviewLocked(enabled: Boolean) {
        if (BuildConfig.DEBUG) _debugPreviewLocked.value = enabled
    }

    /** BillingManager から購入状態を反映する(`MeetingNotesApp` から呼ぶ)。 */
    fun setPro(pro: Boolean) {
        _isPro.value = pro
    }

    /** 有料機能のロック表示を有効にするか(デバッグプレビュー中は常に true)。 */
    val gatingEnabled: Boolean
        get() = BuildConfig.PRO_GATING_ENABLED || _debugPreviewLocked.value

    /** Pro 加入済みか(スナップショット)。 */
    val isPro: Boolean
        get() = _isPro.value

    /**
     * UI をロック表示にすべきか(未加入 かつ ゲーティング有効)。
     * デバッグプレビュー中は実際の購入状態に関わらず常に true(無料ユーザー視点を確実に再現するため)。
     */
    val shouldLock: Boolean
        get() = _debugPreviewLocked.value || (gatingEnabled && !_isPro.value)
}
