package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
) {
    LaunchedEffect(Unit) {
        if (!state.started) onRunDiagnostics()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (state.isRunning) {
                    stringResource(R.string.network_diagnostics_running)
                } else {
                    stringResource(R.string.network_diagnostics_summary)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = onRunDiagnostics,
                enabled = !state.isRunning,
            ) {
                Text(
                    text = stringResource(R.string.network_diagnostics_run),
                    maxLines = 1,
                )
            }
        }

        DiagnosticsCard(stringResource(R.string.network_diagnostics_config_title)) {
            ConfigRow(
                label = stringResource(R.string.domain_name),
                value = Preferences.baseUrl,
            )
            ConfigRow(
                label = stringResource(R.string.use_built_in_hosts),
                value = if (Preferences.useBuiltInHosts) {
                    stringResource(R.string.network_diagnostics_enabled)
                } else {
                    stringResource(R.string.network_diagnostics_disabled)
                },
            )
            ConfigRow(
                label = stringResource(R.string.use_doh),
                value = if (Preferences.useDoH) {
                    stringResource(R.string.network_diagnostics_enabled)
                } else {
                    stringResource(R.string.network_diagnostics_disabled)
                },
            )
            ConfigRow(
                label = stringResource(R.string.proxy),
                value = if (Preferences.proxyType == 0) {
                    stringResource(R.string.network_diagnostics_disabled)
                } else {
                    "${Preferences.proxyType} · ${Preferences.proxyIp}:${Preferences.proxyPort}"
                },
            )
        }

        DiagnosticsCard(stringResource(R.string.network_diagnostics_result_title)) {
            state.items.forEachIndexed { index, item ->
                if (index > 0) {
                    RowDivider()
                }
                CheckRow(item = item)
            }
        }

        state.conclusion?.let { conclusion ->
            DiagnosticsCard(stringResource(R.string.network_diagnostics_conclusion_title)) {
                Text(
                    text = conclusion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                state.suggestion?.let { suggestion ->
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

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
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
        )
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusBadge(item.status)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(item.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            item.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        item.metric?.takeIf { it.isNotBlank() }?.let { metric ->
            Text(
                text = metric,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: NetworkCheckStatus) {
    val dark = isSystemInDarkTheme()
    val okColor = if (dark) Color(0xFF8FD6A8) else Color(0xFF2C6E49)
    val warnColor = if (dark) Color(0xFFFFB95C) else Color(0xFFA15C00)
    when (status) {
        NetworkCheckStatus.RUNNING -> CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        NetworkCheckStatus.PASS -> Badge("✓", okColor)
        NetworkCheckStatus.WARN -> Badge("!", warnColor)
        NetworkCheckStatus.FAIL -> Badge("✕", MaterialTheme.colorScheme.error)
        NetworkCheckStatus.SKIPPED -> Badge("–", MaterialTheme.colorScheme.outline)
        NetworkCheckStatus.PENDING -> Badge("·", MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .padding(top = 1.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun RowDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 44.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}