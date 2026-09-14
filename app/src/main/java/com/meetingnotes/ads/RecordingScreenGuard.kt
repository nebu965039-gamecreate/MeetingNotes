package com.meetingnotes.ads

/**
 * 録音画面(カウントダウン・録音中・停止処理・文字起こし中・編集中のいずれか)が
 * 現在表示されているかどうかのフラグ(2026-09-21〜、App Openアド追加にあわせて新設)。
 *
 * `RecordingScreen` の `DisposableEffect` で true/false を切り替える。`MeetingViewModel` は
 * NavHost外側で1つだけ生成されアプリ全体で生存するため、録音フェーズが「Editing」のまま
 * 画面だけ離脱している、といった状態がありうる。ViewModelのフェーズではなく
 * 「その画面が実際に表示されているか」を素直に表すシンプルな可変フラグとして
 * `AppOpenAdController` の表示可否判定に使う(録音中にアプリを一度バックグラウンドへ回してから
 * 戻ったときに、広告で録音画面が覆われてしまうのを防ぐため)。
 */
object RecordingScreenGuard {
    @Volatile
    var isActive: Boolean = false
}
