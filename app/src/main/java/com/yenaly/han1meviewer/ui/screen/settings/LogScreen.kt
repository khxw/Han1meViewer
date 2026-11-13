package com.yenaly.han1meviewer.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.log.HLumberjack
import com.yenaly.han1meviewer.logic.log.HLumberjack.LogEntry
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.delay

/**
 * 日志查看页：展示 HLumberjack 记录的 DNS 解析与 HTTP 请求日志，
 * 支持开关、清空、复制与分享（用于向开发者反馈问题时携带诊断信息）。
 */
@Composable
fun LogScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(Preferences.enableLogging) }
    var entries by remember { mutableStateOf(HLumberjack.entries()) }

    // 轮询刷新：网络请求日志随时产生，1 秒粒度足够查看
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            entries = HLumberjack.entries()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingSwitchItem(
            title = stringResource(R.string.log_enable),
            summary = stringResource(R.string.log_enable_summary),
            checked = enabled,
            iconRes = R.drawable.baseline_bug_report_24,
            onCheckedChange = { value ->
                enabled = value
                Preferences.preferenceSp.edit {
                    putBoolean(SettingsPreferenceKeys.ENABLE_LOGGING, value)
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                HLumberjack.clear()
                entries = HLumberjack.entries()
            }) {
                Text(stringResource(R.string.log_clear))
            }
            TextButton(onClick = {
                copyLogs(context)
                showShortToast(R.string.log_copied)
            }) {
                Text(stringResource(R.string.log_copy))
            }
            TextButton(onClick = { shareLogs(context) }) {
                Text(stringResource(R.string.log_share))
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                // 日志轮询整体替换，关闭入场动画避免闪烁
                enableItemAnimation = false,
            ) {
                items(
                    items = entries,
                    key = { it.id },
                ) { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val color = when (entry.level) {
        'E' -> MaterialTheme.colorScheme.error
        'W' -> MaterialTheme.colorScheme.tertiary
        'I' -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = entry.formatted(),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
    )
}

private fun copyLogs(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Han1meViewer Log", HLumberjack.exportText()))
}

private fun shareLogs(context: Context) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Han1meViewer Log")
        putExtra(Intent.EXTRA_TEXT, HLumberjack.exportText())
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}
