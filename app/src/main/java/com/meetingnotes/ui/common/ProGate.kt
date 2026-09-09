package com.meetingnotes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.billing.ProAccess
import com.meetingnotes.ui.theme.OnProGold
import com.meetingnotes.ui.theme.ProGold

/**
 * サブスク(Pro)限定機能のラッパー。
 * [locked] のとき中身へのタップを遮って [onLockedTap] を呼び、左上隅に「Pro」バッジを付ける。
 * バッジは中身の上端より上にはみ出す(中身の位置はずらさない)ので、
 * 呼び出し側で上に 12dp 程度の余白を確保しておくこと。
 * 中身のグレーアウトや金色の枠・塗りは呼び出し側で指定する。
 */
@Composable
fun ProGate(
    locked: Boolean,
    onLockedTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!locked) {
        Box(modifier) { content() }
        return
    }
    Box(modifier) {
        content()
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClick = onLockedTap)
        )
        ProBadge(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = 4.dp, y = (-11).dp)
        )
    }
}

@Composable
private fun ProBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(ProGold)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.WorkspacePremium,
            contentDescription = "Pro 限定",
            tint = OnProGold,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "Pro",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = OnProGold
        )
    }
}

/**
 * Pro 限定機能をタップしたときの案内 + 登録導線。
 * 商品(`BillingManager.monthlyProduct`)が取れていれば「登録する」ボタンで購入フローを起動する。
 * 取れていない(Play 未対応・商品未登録など)ときは登録不可の旨を出す。
 */
@Composable
fun ProPaywallDialog(featureName: String, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as MeetingNotesApp
    val activity = LocalActivity.current
    val product by app.billingManager.monthlyProduct.collectAsState()
    val isPro by ProAccess.isProFlow.collectAsState()

    val priceLabel = app.billingManager.monthlyPriceLabel()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = ProGold)
        },
        title = { Text(if (isPro) "Pro をご利用中です" else "Pro 限定の機能です") },
        text = {
            Column {
                if (isPro) {
                    Text("「$featureName」を含む Pro 機能をご利用いただけます。")
                } else {
                    Text("「$featureName」はサブスクリプション(Pro)でご利用いただけます。")
                    Text(
                        buildString {
                            append("・録音の要約→文字起こしの根拠リンク\n")
                            append("・ヒアリング分析\n")
                            append("・売上・実績ビュー / 売上予測\n")
                            append("・エクスポート形式・透かし・PDFパスワード")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (product == null) {
                        Text(
                            "現在ご登録いただけません。しばらくしてからお試しください。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isPro || product == null || activity == null) {
                TextButton(onClick = onDismiss) { Text("閉じる") }
            } else {
                TextButton(onClick = {
                    app.billingManager.launchPurchase(activity)
                    onDismiss()
                }) {
                    Text(if (priceLabel != null) "登録する（$priceLabel / 月）" else "登録する")
                }
            }
        },
        dismissButton = if (!isPro && product != null && activity != null) {
            { TextButton(onClick = onDismiss) { Text("キャンセル") } }
        } else null
    )
}
