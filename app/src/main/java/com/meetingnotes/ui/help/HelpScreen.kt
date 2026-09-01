package com.meetingnotes.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使い方・ヘルプ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HelpSection(
                "基本的な流れ",
                """
                1. クライアントを選び「録音開始」をタップ
                2. 3秒のカウントダウン後、録音が始まります
                3. 商談が終わったら「停止」
                4. 文字起こしの内容を確認・修正
                5. 「この内容で要約する」でAIが議事録を作成
                6. タイトルを付けて保存
                """.trimIndent()
            )
            HelpSection(
                "AIがまとめる項目",
                "サマリー / 決定事項 / 懸念点・注意点 / ToDo(担当者・期限つき) / 次回打ち合わせ。" +
                    "該当する内容が話されていない項目は「(なし)」と表示されます。"
            )
            HelpSection(
                "無料枠と広告について",
                """
                ・要約は毎月5回まで無料です(毎月1日にリセット)
                ・使い切った後は、動画広告を1本見ると1回分が追加されます
                ・要約に失敗した場合、その回分は自動で戻ります
                """.trimIndent()
            )
            HelpSection(
                "長い商談のコツ",
                "1回の要約でまとめられるのは約60〜80分ぶんまでです。" +
                    "それより長い商談は、区切りのよいところで一度「停止」して保存し、" +
                    "続きを新しい録音として分けて記録してください。"
            )
            HelpSection(
                "きれいに文字起こしするコツ",
                """
                ・静かな場所で、端末を話し手の近くに置く
                ・一人ずつ、はっきり話す
                ・「えー」「あのー」などは自動で除去されます
                ・誤変換は要約前の編集画面で直せます
                """.trimIndent()
            )
            HelpSection(
                "整理のしかた",
                """
                ・クライアント: 商談相手ごとにまとめる単位
                ・フォルダ: クライアント内で商談を案件別に分ける
                ・グループ: クライアントどうしをまとめる(例:継続案件 / 新規リード)
                フォルダ・グループはヘッダーのアイコンやメニューから作成できます。
                """.trimIndent()
            )
            HelpSection(
                "エクスポート",
                "商談の詳細画面から、議事録を PDF・Word・メール文面 として書き出せます。" +
                    "PDF は書き出し時に透かしの有無・位置を選べます。"
            )
            HelpSection(
                "プライバシー",
                "録音した音声はファイルとして保存されず、外部にも送信されません。" +
                    "要約のときだけ、文字起こしテキストが暗号化通信でAIに送信されます。" +
                    "クライアント名や議事録は端末内にのみ保存されます。"
            )
            HelpSection(
                "お問い合わせ",
                "不具合・ご要望は contact.manaapps@gmail.com までご連絡ください。"
            )
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}
