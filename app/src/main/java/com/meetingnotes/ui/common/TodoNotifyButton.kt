package com.meetingnotes.ui.common

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.meetingnotes.notifications.NotificationHelper
import com.meetingnotes.notifications.TodoNotificationScheduler
import com.meetingnotes.ui.theme.CreateActionAmber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * ToDo 1件の通知予約ボタン(2026-09-16、旧 `SnoozeMenu` を置き換え)。
 * 「スヌーズ(=一覧から一時的に隠す)」は「ただ再表示されるだけで気づきにくい」という
 * フィードバックを受けて廃止し、代わりに**ユーザーが指定した日時に端末通知を送る**方式にした。
 * ToDo は通知予約の有無に関わらず常に一覧・件数に残る(隠さない)。
 *
 * [notifyAt] が null(未予約)ならタップで直接、日時指定ダイアログ(`NextMeetingDateTimeDialog`、
 * プリセットは無くフル手動指定)を開く。予約済みならベルが塗りつぶし表示になり、タップで
 * 「日時を変更」「通知を解除」の小メニューを出す。WorkManager への登録・解除はこのボタンが
 * 直接行う(ViewModel は Context を持たないため)。DB への反映は [onNotifyAtChange] で呼び出し側に委譲。
 */
@Composable
fun TodoNotifyButton(
    todoId: Long,
    notifyAt: Long?,
    onNotifyAtChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val scheduled = notifyAt != null

    // 通知権限が無い(Android 13+)と WorkManager 側は発火しても静かに送信をスキップするだけなので、
    // 予約の直前にダメ元でリクエストしておく(既存の打ち合わせリマインドと同じ権限を共有)。
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 結果に関わらず予約自体は行う(通知が出ない場合の告知は現状無し、既存のリマインド機能と同方針) */ }

    Box(modifier) {
        IconButton(onClick = { if (scheduled) menuOpen = true else showPicker = true }) {
            Icon(
                if (scheduled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                contentDescription = if (scheduled) "通知予約中" else "通知を設定",
                tint = if (scheduled) CreateActionAmber else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("日時を変更") },
                onClick = { menuOpen = false; showPicker = true }
            )
            DropdownMenuItem(
                text = { Text("通知を解除") },
                onClick = {
                    menuOpen = false
                    TodoNotificationScheduler.cancel(context, todoId)
                    onNotifyAtChange(null)
                }
            )
        }
    }

    if (showPicker) {
        val initial = notifyAt
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
            ?: LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        NextMeetingDateTimeDialog(
            initial = initial,
            initialHasTime = true,
            title = "通知日時を設定",
            onDismiss = { showPicker = false },
            onConfirm = { dt, _ ->
                val millis = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                // minSdk 33 のため POST_NOTIFICATIONS は常に実行時権限が必要。
                if (!NotificationHelper.hasPermission(context)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                TodoNotificationScheduler.schedule(context, todoId, millis)
                onNotifyAtChange(millis)
                showPicker = false
            }
        )
    }
}
