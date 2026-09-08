package com.meetingnotes.data.model

/**
 * 商談フェーズ(F3)。AI が商談内容から推定し(`MeetingEntity.dealPhase`)、
 * ユーザーが必要に応じて上書きする(`MeetingEntity.phaseOverride`)。
 * DB・Worker・アプリで共通の識別子は [wireValue]。
 */
enum class DealPhase(val wireValue: String, val label: String) {
    FIRST_CONTACT("first_contact", "初回接触"),
    HEARING("hearing", "ヒアリング"),
    PROPOSAL("proposal", "提案"),
    QUOTED("quoted", "見積提示"),
    CONSIDERING("considering", "検討中"),
    WON("won", "成約"),
    ON_HOLD("on_hold", "保留"),
    LOST("lost", "失注");

    /** 「まだ動いている案件」= フォロー対象になりうるフェーズ。 */
    val isActive: Boolean
        get() = this != WON && this != LOST

    companion object {
        fun fromWire(value: String?): DealPhase? =
            value?.let { v -> entries.firstOrNull { it.wireValue == v } }

        /** Worker の tool スキーマ enum と揃える。 */
        val wireValues: List<String> = entries.map { it.wireValue }
    }
}
