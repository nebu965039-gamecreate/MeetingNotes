package com.meetingnotes.util

/**
 * 自由入力テキストフィールドの文字数上限(2026-09-21)。
 *
 * これまで金額・受注確度以外のテキスト入力には上限が無く、極端に長い文字列を入れると
 * ヘッダー(`TopAppBar`)やPDFの透かしなど、`maxLines`/`overflow` で保護されていない箇所で
 * 表示が崩れる恐れがあった。`OutlinedTextField` の `onValueChange` で
 * `it.take(InputLimits.XXX)` のように使い、入力時点で静かに切り詰める
 * (エラー表示やカウンタは出さない。既存の `probText.take(3)` と同じ方針)。
 */
object InputLimits {
    /** クライアント名・グループ名・フォルダ名・案件名など、一覧行や見出しに使う短い名前。 */
    const val NAME = 50
    /** 打ち合わせタイトル・予定タイトルなど、名前よりやや長い見出し。 */
    const val TITLE = 100
    /** ToDoの内容・失注理由(自由記述)など、1〜2行想定の短文。 */
    const val SHORT_TEXT = 200
    /** 備考・メモ・予定の説明など、複数行想定の長文。 */
    const val LONG_TEXT = 500
    /** メールアドレス・URL。 */
    const val CONTACT = 200
    /** 電話番号。 */
    const val PHONE = 30
    /** メールテンプレート本文。 */
    const val EMAIL_BODY = 4000
    /** PDF透かし文字。中央配置では56ptで回転描画されるため特に短く。 */
    const val WATERMARK = 30
}
