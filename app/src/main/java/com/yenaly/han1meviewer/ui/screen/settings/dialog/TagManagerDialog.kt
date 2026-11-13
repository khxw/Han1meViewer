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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R

/**
 * 标签管家：浏览本地标签目录，搜索并快速切换屏蔽状态。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManagerDialog(
    initialTags: Set<String>,
    allTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var blockedTags by rememberSaveable(initialTags) {
        mutableStateOf(initialTags.toList().sorted())
    }
    var query by rememberSaveable { mutableStateOf("") }
    var customTag by rememberSaveable { mutableStateOf("") }

    val normalizedQuery = query.trim()
    val blockedLookup = remember(blockedTags) {
        blockedTags.map { it.lowercase() }.toSet()
    }
    val filteredTags = remember(allTags, normalizedQuery, blockedLookup) {
        allTags
            .filter { tag ->
                normalizedQuery.isEmpty() || tag.contains(normalizedQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<String> { it.lowercase() in blockedLookup }
                    .thenBy { it.lowercase() },
            )
    }

    val toggleTag: (String) -> Unit = { rawTag ->
        val tag = rawTag.trim()
        if (tag.isNotEmpty()) {
            val existing = blockedTags.firstOrNull { it.equals(tag, ignoreCase = true) }
            blockedTags = if (existing == null) {
                blockedTags + tag
            } else {
                blockedTags - existing
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_manager_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.blocked_tags_dialog_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(R.string.tag_manager_blocked),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (blockedTags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.blocked_tags_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        blockedTags.sorted().forEach { tag ->
                            AssistChip(
                                onClick = { toggleTag(tag) },
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
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.tag_manager_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.tag_manager_catalog, filteredTags.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (filteredTags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        filteredTags.take(200).forEach { tag ->
                            FilterChip(
                                selected = tag.lowercase() in blockedLookup,
                                onClick = { toggleTag(tag) },
                                label = { Text(tag) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customTag,
                    onValueChange = { customTag = it },
                    placeholder = { Text(stringResource(R.string.blocked_tags_input_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        toggleTag(customTag)
                        customTag = ""
                    },
                    enabled = customTag.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.add))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(blockedTags.toSet()) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
