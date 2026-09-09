package com.meetingnotes.ui.settings

import android.Manifest
import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.MeetingNotesApp
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.backup.BackupManager
import com.meetingnotes.notifications.NotificationHelper
import com.meetingnotes.ui.theme.ThemeMode
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val app = context.applicationContext as MeetingNotesApp
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(application))
    val remindersEnabled by viewModel.remindersEnabled.collectAsState()
    val themeMode = app.themeModeState.value

    var showThemeDialog by remember { mutableStateOf(false) }

    val backupState by viewModel.backupState.collectAsState()
    var exportPassword by remember { mutableStateOf("") }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showRestoreWarning by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri, exportPassword.takeIf { it.isNotEmpty() })
        exportPassword = ""
    }
    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImportUri = uri }

    LaunchedEffect(backupState) {
        val s = backupState
        if (s is SettingsViewModel.BackupState.Done) {
            Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
            if (s.restart) BackupManager.restartApp(context) else viewModel.clearBackupState()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setRemindersEnabled(granted)
    }

    val toggleReminders: (Boolean) -> Unit = { want ->
        if (want && !NotificationHelper.hasPermission(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setRemindersEnabled(want)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        bottomBar = { BannerAdView(Modifier.navigationBarsPadding()) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("打ち合わせのリマインド", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "次回打ち合わせの前日と当日にお知らせします。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = remindersEnabled, onCheckedChange = toggleReminders)
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("背景(テーマ)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                themeMode.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                val working = backupState is SettingsViewModel.BackupState.Working
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "データのバックアップ",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        SettingActionRow(
                            title = "バックアップを作成",
                            subtitle = "全クライアント・商談・ToDo・案件・予定を1ファイルに書き出します。",
                            enabled = !working,
                            onClick = { showExportPasswordDialog = true }
                        )
                        SettingActionRow(
                            title = "バックアップから復元",
                            subtitle = "現在のデータをすべて置き換えます。機種変更・再インストール時に使います。",
                            enabled = !working,
                            onClick = { showRestoreWarning = true }
                        )
                        if (working) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("処理中…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showThemeDialog) {
        ThemeModePickerDialog(
            current = themeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = { mode ->
                app.setThemeMode(mode)
                showThemeDialog = false
            }
        )
    }

    if (showExportPasswordDialog) {
        BackupPasswordDialog(
            title = "バックアップを作成",
            message = "パスワードを設定すると、ファイルを暗号化します(空欄可)。",
            confirmLabel = "作成",
            onDismiss = { showExportPasswordDialog = false; exportPassword = "" },
            onConfirm = { pw ->
                exportPassword = pw
                showExportPasswordDialog = false
                createBackupLauncher.launch("商談メモ-backup-${LocalDate.now()}.json")
            }
        )
    }

    if (showRestoreWarning) {
        AlertDialog(
            onDismissRequest = { showRestoreWarning = false },
            title = { Text("バックアップから復元") },
            text = { Text("現在アプリに入っているデータはすべて削除され、選んだバックアップの内容に置き換わります。よろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreWarning = false
                    openBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text("ファイルを選ぶ") }
            },
            dismissButton = { TextButton(onClick = { showRestoreWarning = false }) { Text("キャンセル") } }
        )
    }

    pendingImportUri?.let { uri ->
        BackupPasswordDialog(
            title = "バックアップから復元",
            message = "パスワード付きのバックアップの場合は入力してください(未設定なら空欄)。",
            confirmLabel = "復元",
            onDismiss = { pendingImportUri = null },
            onConfirm = { pw ->
                pendingImportUri = null
                viewModel.importBackup(uri, pw.takeIf { it.isNotEmpty() })
            }
        )
    }

    (backupState as? SettingsViewModel.BackupState.Error)?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearBackupState() },
            title = { Text("エラー") },
            text = { Text(err.message) },
            confirmButton = { TextButton(onClick = { viewModel.clearBackupState() }) { Text("閉じる") } }
        )
    }
}

@Composable
private fun SettingActionRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("パスワード（任意）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
private fun ThemeModePickerDialog(
    current: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("背景(テーマ)") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == mode, onClick = { onSelect(mode) })
                        Text(mode.label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}
