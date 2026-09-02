package com.meetingnotes.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AccentPurple = Color(0xFF6750A4)
private val AccentBlue = Color(0xFF1565C0)
private val AccentGreen = Color(0xFF2E7D32)
private val AccentSlate = Color(0xFF455A64)

private const val CONTACT_EMAIL = "contact.manaapps@gmail.com"

private sealed interface HelpBody {
    data class Prose(val text: String) : HelpBody
    data class Bullets(val items: List<String>) : HelpBody
    data class Steps(val items: List<String>) : HelpBody
}

private data class HelpTopic(
    val icon: ImageVector,
    val accent: Color,
    val title: String,
    val body: HelpBody
)

private val helpTopics = listOf(
    HelpTopic(
        Icons.Filled.PlayCircleOutline, AccentPurple, "基本的な流れ",
        HelpBody.Steps(
            listOf(
                "クライアントを選び「録音開始」をタップ",
                "3秒のカウントダウン後、録音が始まります",
                "商談が終わったら「停止」",
                "文字起こしの内容を確認・修正",
                "「この内容で要約する」でAIが議事録を作成",
                "タイトルを付けて保存"
            )
        )
    ),
    HelpTopic(
        Icons.Filled.AutoAwesome, AccentPurple, "AIがまとめる項目",
        HelpBody.Prose(
            "サマリー / 決定事項 / 懸念点・注意点 / ToDo(担当者・期限つき) / 次回打ち合わせ。" +
                "該当する内容が話されていない項目は「(なし)」と表示されます。"
        )
    ),
    HelpTopic(
        Icons.Filled.CardGiftcard, AccentGreen, "無料枠と広告について",
        HelpBody.Bullets(
            listOf(
                "要約は毎月5回まで無料です(毎月1日にリセット)",
                "使い切った後は、動画広告を1本見ると1回分が追加されます",
                "要約に失敗した場合、その回分は自動で戻ります"
            )
        )
    ),
    HelpTopic(
        Icons.Filled.Schedule, AccentGreen, "長い商談のコツ",
        HelpBody.Prose(
            "1回の要約でまとめられるのは約60〜80分ぶんまでです。" +
                "それより長い商談は、区切りのよいところで一度「停止」して保存し、" +
                "続きを新しい録音として分けて記録してください。"
        )
    ),
    HelpTopic(
        Icons.Filled.Mic, AccentGreen, "きれいに文字起こしするコツ",
        HelpBody.Bullets(
            listOf(
                "静かな場所で、端末を話し手の近くに置く",
                "一人ずつ、はっきり話す",
                "「えー」「あのー」などは自動で除去されます",
                "誤変換は要約前の編集画面で直せます"
            )
        )
    ),
    HelpTopic(
        Icons.Filled.FolderOpen, AccentBlue, "整理のしかた",
        HelpBody.Bullets(
            listOf(
                "クライアント: 商談相手ごとにまとめる単位",
                "フォルダ: クライアント内で商談を案件別に分ける",
                "グループ: クライアントどうしをまとめる(例:継続案件 / 新規リード)",
                "フォルダ・グループは画面上部の青いフォルダアイコンから作成できます"
            )
        )
    ),
    HelpTopic(
        Icons.Filled.Download, AccentBlue, "エクスポート",
        HelpBody.Bullets(
            listOf(
                "商談の詳細画面から「共有する / デバイスに保存」を選びます",
                "議事録ぜんぶ … PDF・Word・Markdown",
                "ToDoリストだけ … Excel・CSV(タスク管理ツール向け)",
                "次回打ち合わせ … カレンダー(.ics)(日時が決まっている場合)",
                "PDF は書き出し時に透かしの有無・位置を選べます"
            )
        )
    ),
    HelpTopic(
        Icons.Filled.Lock, AccentSlate, "プライバシー",
        HelpBody.Prose(
            "録音した音声はファイルとして保存されず、外部にも送信されません。" +
                "要約のときだけ、文字起こしテキストが暗号化通信でAIに送信されます。" +
                "クライアント名や議事録は端末内にのみ保存されます。"
        )
    )
)

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IntroBand()
            helpTopics.forEach { HelpCard(it) }
            ContactCard()
        }
    }
}

@Composable
private fun IntroBand() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "商談メモの使い方",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "録音するだけで、AIが議事録に整えます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun HelpCard(topic: HelpTopic) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(topic.accent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        topic.icon,
                        contentDescription = null,
                        tint = topic.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = topic.accent
                )
            }

            when (val body = topic.body) {
                is HelpBody.Prose -> Text(
                    body.text,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 25.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is HelpBody.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    body.items.forEach { BulletRow(it, topic.accent) }
                }
                is HelpBody.Steps -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    body.items.forEachIndexed { i, step -> StepRow(i + 1, step, topic.accent) }
                }
            }
        }
    }
}

@Composable
private fun BulletRow(text: String, accent: Color) {
    Row {
        Box(
            modifier = Modifier
                .padding(top = 8.dp, end = 10.dp)
                .size(6.dp)
                .background(accent, CircleShape)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 25.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepRow(number: Int, text: String, accent: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(24.dp)
                .background(accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 25.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ContactCard() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.MailOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "お問い合わせ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "不具合・ご要望はこちらまで",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                SelectionContainer {
                    Text(
                        CONTACT_EMAIL,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
