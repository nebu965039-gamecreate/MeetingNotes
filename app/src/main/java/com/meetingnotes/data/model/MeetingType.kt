package com.meetingnotes.data.model

/**
 * 商談の実施形態。録音開始時にユーザーが選ぶ。
 *  - IN_PERSON: 対面。端末内の音声認識でリアルタイム文字起こし(音声は端末外に出ない)
 *  - REMOTE: リモート会議。録音音声をサーバー(Cloudflare Whisper)で文字起こし(Pro / 無料お試し)
 */
enum class MeetingType(val wireValue: String, val label: String) {
    IN_PERSON("in_person", "対面"),
    REMOTE("remote", "リモート会議");

    companion object {
        fun fromWire(value: String?): MeetingType? =
            value?.let { v -> entries.firstOrNull { it.wireValue == v } }
    }
}
