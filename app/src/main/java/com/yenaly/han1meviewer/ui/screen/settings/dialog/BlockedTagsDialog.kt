package com.yenaly.han1meviewer.ui.screen.settings.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.yenaly_libs.utils.showShortToast

/**
 * 屏蔽项管理对话框（屏蔽标签/屏蔽关键词共用）
 *
 * 支持添加/删除屏蔽项，确认后通过 [onConfirm] 回传新的列表。
 * 文案默认展示屏蔽标签，可通过参数切换为屏蔽关键词。
 *
 * @author Yenaly Liew
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockedTagsDialog(
    initialTags: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    titleRes: Int = R.string.blocked_tags_dialog_title,
    summaryRes: Int = R.string.blocked_tags_dialog_summary,
    emptyRes: Int = R.string.blocked_tags_empty,
    inputHintRes: Int = R.string.blocked_tags_input_hint,
    duplicatedRes: Int = R.string.blocked_tags_duplicated,
) {
    var tags by rememberSaveable(initialTags) { mutableStateOf(initialTags.toList()) }
    var input by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(summaryRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(emptyRes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag ->
                            AssistChip(
                                onClick = { tags = tags - tag },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.remove),
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(inputHintRes)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                TextButton(
                    onClick = {
                        val tag = input.trim()
                        if (tag.isEmpty()) return@TextButton
                        val duplicated = tags.any { it.equals(tag, ignoreCase = true) }
                        if (duplicated) {
                            showShortToast(duplicatedRes)
                            return@TextButton
                        }
                        tags = listOf(tag) + tags
                        input = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.add))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(tags.toSet()) },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
