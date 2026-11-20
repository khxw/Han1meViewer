package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.viewmodel.NetworkCheckItem
import com.yenaly.han1meviewer.ui.viewmodel.NetworkCheckStatus
import com.yenaly.han1meviewer.ui.viewmodel.NetworkDiagnosticsUiState

/**
 * 网络诊断页面：配置概览 + 逐项检查结果 + 结论建议。
 */
@Composable
fun NetworkDiagnosticsScreen(
    state: NetworkDiagnosticsUiState,
    onRunDiagnostics: () -> Unit,
    onExportLogs: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (!state.started) onRunDiagnostics()
    }

    val overallStatus = when {
        !state.started -> NetworkCheckStatus.PENDING
        state.isRunning -> NetworkCheckStatus.RUNNING
        state.items.any { it.status == NetworkCheckStatus.FAIL } -> NetworkCheckStatus.FAIL
        state.items.any { it.status == NetworkCheckStatus.WARN } -> NetworkCheckStatus.WARN
        state.items.any { it.status == NetworkCheckStatus.PASS } -> NetworkCheckStatus.PASS
        else -> NetworkCheckStatus.PENDING
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OverallCard(state = state, status = overallStatus)
            ConfigChips(state = state)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.network_diagnostics_result_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.network_diagnostics_item_count,
                        state.items.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DiagnosticsCard(title = "") {
                state.items.forEachIndexed { index, item ->
                    if (index > 0) RowDivider()
                    CheckRow(item = item)
                }
            }

            state.suggestion?.takeIf { it.isNotBlank() }?.let { suggestion ->
                SuggestionCard(suggestion = suggestion)
            }
        }

        BottomActionBar(
            isRunning = state.isRunning,
            onExportLogs = onExportLogs,
            onRunDiagnostics = onRunDiagnostics,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun OverallCard(
    state: NetworkDiagnosticsUiState,
    status: NetworkCheckStatus,
) {
    val accent = statusColor(status)
    val shape = RoundedCornerShape(24.dp)
    val title = when (status) {
        NetworkCheckStatus.RUNNING -> stringResource(R.string.network_diagnostics_result_running)
        NetworkCheckStatus.FAIL -> stringResource(R.string.network_diagnostics_result_fail)
        NetworkCheckStatus.WARN -> stringResource(R.string.network_diagnostics_result_warn)
        NetworkCheckStatus.PASS -> stringResource(R.string.network_diagnostics_result_ok)
        NetworkCheckStatus.SKIPPED,
        NetworkCheckStatus.PENDING,
        -> stringResource(R.string.network_diagnostics_result_ready)
    }
    val description = state.conclusion
        ?: if (state.isRunning) {
            stringResource(R.string.network_diagnostics_running)
        } else {
            stringResource(R.string.network_diagnostics_summary)
        }
    val networkSummary = state.networkTransport.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.28f)), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent),
            )
            Text(
                text = stringResource(
                    if (state.isRunning) {
                        R.string.network_diagnostics_running
                    } else {
                        R.string.network_diagnostics_completed
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent.copy(alpha = 0.13f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
            ) {
                Text(
                    text = statusLabel(status),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = networkSummary.ifBlank {
                    Preferences.baseUrl.removePrefix("https://").trimEnd('/')
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    if (state.isRunning) {
                        R.string.network_diagnostics_running
                    } else {
                        R.string.network_diagnostics_completed
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ConfigChips(state: NetworkDiagnosticsUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConfigChip(
            label = stringResource(R.string.domain_name),
            value = Preferences.baseUrl.removePrefix("https://").trimEnd('/'),
        )
        state.networkTransport?.takeIf { it.isNotBlank() }?.let {
            ConfigChip(
                label = stringResource(R.string.network_diagnostics_check_network_environment),
                value = it,
            )
        }
        state.carrierLabel?.takeIf { it.isNotBlank() }?.let {
            ConfigChip(
                label = stringResource(R.string.network_diagnostics_carrier),
                value = it,
            )
        }
        ConfigChip(
            label = "Hosts",
            value = enabledLabel(Preferences.useBuiltInHosts),
        )
        ConfigChip(
            label = "DoH",
            value = enabledLabel(Preferences.useDoH),
        )
        ConfigChip(
            label = stringResource(R.string.proxy),
            value = if (Preferences.proxyType == 0) {
                stringResource(R.string.network_diagnostics_disabled)
            } else {
                "${Preferences.proxyIp}:${Preferences.proxyPort}"
            },
        )
    }
}

@Composable
private fun ConfigChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.22f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = stringResource(R.string.network_diagnostics_actions_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = suggestion.cleanSuggestionPrefix(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BottomActionBar(
    isRunning: Boolean,
    onExportLogs: () -> Unit,
    onRunDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            OutlinedButton(
                onClick = onExportLogs,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(15.dp),
            ) {
                Text(stringResource(R.string.network_diagnostics_export_logs))
            }
            Button(
                onClick = onRunDiagnostics,
                enabled = !isRunning,
                modifier = Modifier.weight(1.35f),
                shape = RoundedCornerShape(15.dp),
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.network_diagnostics_run_again))
                }
            }
        }
    }
}

@Composable
private fun enabledLabel(enabled: Boolean): String = stringResource(
    if (enabled) {
        R.string.network_diagnostics_enabled
    } else {
        R.string.network_diagnostics_disabled
    },
)

@Composable
private fun DiagnosticsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
            )
        }
        content()
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun CheckRow(item: NetworkCheckItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(item.status)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StatusText(item.status)
                item.metric?.takeIf { it.isNotBlank() }?.let { metric ->
                    Text(
                        text = metric,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                    )
                }
            }
            item.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: NetworkCheckStatus) {
    val accent = statusColor(status)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.11f))
            .border(
                BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
                RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (status == NetworkCheckStatus.RUNNING) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                strokeWidth = 2.dp,
                color = accent,
            )
        } else {
            Text(
                text = statusGlyph(status),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

@Composable
private fun StatusText(status: NetworkCheckStatus) {
    val accent = statusColor(status)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.10f),
    ) {
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
private fun statusColor(status: NetworkCheckStatus): Color = when (status) {
    NetworkCheckStatus.PASS -> MaterialTheme.colorScheme.primary
    NetworkCheckStatus.WARN -> MaterialTheme.colorScheme.tertiary
    NetworkCheckStatus.FAIL -> MaterialTheme.colorScheme.error
    NetworkCheckStatus.RUNNING -> MaterialTheme.colorScheme.primary
    NetworkCheckStatus.SKIPPED,
    NetworkCheckStatus.PENDING,
    -> MaterialTheme.colorScheme.outline
}

@Composable
private fun statusLabel(status: NetworkCheckStatus): String = stringResource(
    when (status) {
        NetworkCheckStatus.PASS -> R.string.network_diagnostics_status_pass
        NetworkCheckStatus.WARN -> R.string.network_diagnostics_status_warn
        NetworkCheckStatus.FAIL -> R.string.network_diagnostics_status_fail
        NetworkCheckStatus.RUNNING -> R.string.network_diagnostics_status_running
        NetworkCheckStatus.SKIPPED -> R.string.network_diagnostics_status_skipped
        NetworkCheckStatus.PENDING -> R.string.network_diagnostics_status_pending
    }
)

private fun statusGlyph(status: NetworkCheckStatus): String = when (status) {
    NetworkCheckStatus.PASS -> "✓"
    NetworkCheckStatus.WARN -> "△"
    NetworkCheckStatus.FAIL -> "!"
    NetworkCheckStatus.SKIPPED -> "–"
    NetworkCheckStatus.PENDING -> "·"
    NetworkCheckStatus.RUNNING -> ""
}

private fun String.cleanSuggestionPrefix(): String = removePrefix("建议：")
    .removePrefix("建議：")
    .removePrefix("Suggestion: ")
    .removePrefix("提案：")
