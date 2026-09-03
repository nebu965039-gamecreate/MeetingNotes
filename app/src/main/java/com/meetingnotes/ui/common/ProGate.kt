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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

/** Pro 限定機能をタップしたときの案内。Billing 未実装のため登録導線は近日提供。 */
@Composable
fun ProPaywallDialog(featureName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = ProGold)
        },
        title = { Text("Pro 限定の機能です") },
        text = {
            Text("「$featureName」はサブスクリプション(Pro)でご利用いただけます。登録機能は近日提供予定です。")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}
