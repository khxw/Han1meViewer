package com.yenaly.han1meviewer.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.entity.FavoriteEntity
import com.yenaly.han1meviewer.logic.entity.FavoriteGroupEntity
import com.yenaly.han1meviewer.ui.component.ConfirmDialog
import com.yenaly.han1meviewer.ui.component.bottomNavReservedSpacing
import com.yenaly.han1meviewer.ui.component.content.EmptyContent
import com.yenaly.han1meviewer.ui.component.appbar.HanimeScaffold
import com.yenaly.han1meviewer.ui.component.verticalScrollbar
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 全部收藏 Tab */
private const val ALL_TAB = -1

/** 未分组收藏 Tab */
private const val UNGROUPED_TAB = -2

/**
 * 本地收藏（离线收藏）列表页
 *
 * @author Yenaly Liew
 */
@Composable
fun LocalFavoriteScreen(
    favoritesFlow: Flow<List<FavoriteEntity>>,
    groupsFlow: Flow<List<FavoriteGroupEntity>>,
    onBack: () -> Unit,
    onOpenVideo: (FavoriteEntity) -> Unit,
    onDeleteFavorite: (FavoriteEntity) -> Unit,
    onDeleteAllFavorites: () -> Unit,
    onCreateGroup: suspend (String) -> Int,
    onRenameGroup: (FavoriteGroupEntity, String) -> Unit,
    onDeleteGroup: (FavoriteGroupEntity) -> Unit,
    onMoveFavoritesToGroup: (List<Int>, Int?) -> Unit,
    onOpenPosterWall: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val favorites by favoritesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val groups by groupsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var pendingDelete by remember { mutableStateOf<FavoriteEntity?>(null) }
    var showDeleteAllDialog by rememberSaveable { mutableStateOf(false) }

    // 多選批量管理狀態
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var showBatchDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // 本地收藏搜索過濾
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // 分组状态：ALL_TAB 全部 / UNGROUPED_TAB 未分组 / >=0 分组 id
    var selectedTab by rememberSaveable { mutableStateOf(ALL_TAB) }
    var showManageGroupDialog by rememberSaveable { mutableStateOf(false) }
    var showMoveGroupDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(favorites, groups) {
        val validFavoriteIds = favorites.mapTo(mutableSetOf()) { it.id }
        selectedIds = selectedIds.filter { it in validFavoriteIds }
        if (selectedIds.isEmpty()) selectionMode = false

        val validGroupIds = groups.mapTo(mutableSetOf()) { it.id }
        if (selectedTab != ALL_TAB &&
            selectedTab != UNGROUPED_TAB &&
            selectedTab !in validGroupIds
        ) {
            selectedTab = ALL_TAB
        }
    }

    LaunchedEffect(selectedTab, searchQuery) {
        selectedIds = emptyList()
        selectionMode = false
    }

    ConfirmDialog(
        visible = pendingDelete != null,
        title = stringResource(R.string.delete_fav),
        message = stringResource(R.string.sure_to_delete_s, pendingDelete?.title.orEmpty()),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            pendingDelete?.let(onDeleteFavorite)
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )

    ConfirmDialog(
        visible = showBatchDeleteDialog,
        title = stringResource(R.string.delete_fav),
        message = pluralStringResource(R.plurals.sure_to_delete_selected_local_favorites, selectedIds.size, selectedIds.size),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            favorites.filter { it.id in selectedIds }.forEach(onDeleteFavorite)
            selectedIds = emptyList()
            selectionMode = false
            showBatchDeleteDialog = false
        },
        onDismiss = { showBatchDeleteDialog = false },
    )

    ConfirmDialog(
        visible = showDeleteAllDialog,
        title = stringResource(R.string.local_favorite_delete_all_title),
        message = stringResource(R.string.sure_to_delete_all_local_favorites),
        confirmText = stringResource(R.string.watch_history_clear_all),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            onDeleteAllFavorites()
            showDeleteAllDialog = false
        },
        onDismiss = { showDeleteAllDialog = false },
    )

    // 分组管理对话框：新建 / 重命名 / 删除
    FavoriteGroupManageDialog(
        visible = showManageGroupDialog,
        groups = groups,
        onDismiss = { showManageGroupDialog = false },
        onCreate = { name -> scope.launch { onCreateGroup(name) } },
        onRename = onRenameGroup,
        onDelete = onDeleteGroup,
    )

    // 批量移动到分组对话框
    MoveFavoriteGroupDialog(
        visible = showMoveGroupDialog,
        groups = groups,
        onDismiss = { showMoveGroupDialog = false },
        onConfirm = { groupId ->
            onMoveFavoritesToGroup(selectedIds, groupId)
            selectedIds = emptyList()
            selectionMode = false
            showMoveGroupDialog = false
        },
        onCreateAndMove = { name ->
            scope.launch {
                val groupId = onCreateGroup(name)
                onMoveFavoritesToGroup(selectedIds, groupId)
                selectedIds = emptyList()
                selectionMode = false
                showMoveGroupDialog = false
            }
        },
    )

    HanimeScaffold(
        title = stringResource(R.string.local_favorite),
        subtitle = {
            Text(
                text = pluralStringResource(R.plurals.local_favorite_total_count, favorites.size, favorites.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onBack = onBack,
        actions = {
            FilledTonalIconButton(
                onClick = onOpenPosterWall,
                enabled = favorites.isNotEmpty(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_grid_24),
                    contentDescription = stringResource(R.string.favorite_poster_wall),
                )
            }
            FilledIconButton(
                onClick = { showDeleteAllDialog = true },
                enabled = favorites.isNotEmpty()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_delete_24),
                    contentDescription = stringResource(R.string.local_favorite_delete_all_title),
                )
            }
        },
    ) { paddingValues ->
        fun toggleFavoriteSelection(id: Int) {
            selectedIds = if (id in selectedIds) {
                selectedIds - id
            } else {
                selectedIds + id
            }
        }

        val filteredFavorites = remember(favorites, groups, searchQuery, selectedTab) {
            val query = searchQuery.trim()
            favorites.filter { favorite ->
                val inTab = when (selectedTab) {
                    ALL_TAB -> true
                    UNGROUPED_TAB -> favorite.groupId == null
                    else -> favorite.groupId == selectedTab
                }
                inTab && (query.isEmpty() || favorite.title.contains(query, ignoreCase = true))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (favorites.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyContent(
                        hint = stringResource(R.string.local_favorite_empty_title),
                        subHint = stringResource(R.string.local_favorite_empty_description),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 分组 Tab 行：全部 / 未分组 / 各分组 / 新建
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item {
                            FilterChip(
                                selected = selectedTab == ALL_TAB,
                                onClick = { selectedTab = ALL_TAB },
                                label = { Text(stringResource(R.string.local_favorite_group_all)) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedTab == UNGROUPED_TAB,
                                onClick = { selectedTab = UNGROUPED_TAB },
                                label = { Text(stringResource(R.string.local_favorite_group_ungrouped)) },
                            )
                        }
                        items(groups, key = { it.id }) { group ->
                            FilterChip(
                                selected = selectedTab == group.id,
                                onClick = { selectedTab = group.id },
                                label = {
                                    Text(
                                        text = group.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                        item {
                            FilledTonalIconButton(
                                onClick = { showManageGroupDialog = true },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = stringResource(R.string.local_favorite_group_manage),
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_local_favorites)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_search_24),
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.close),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    if (filteredFavorites.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyContent(
                                hint = stringResource(
                                    if (searchQuery.isNotBlank()) R.string.no_matching_local_favorites
                                    else R.string.local_favorite_group_empty
                                )
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                // 本地收藏页不显示悬浮导航栏，仅让开系统导航条
                                .padding(bottom = bottomNavReservedSpacing())
                                .navigationBarsPadding(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(filteredFavorites, key = { it.id }) { favorite ->
                                FavoriteCard(
                                    favorite = favorite,
                                    onClick = {
                                        if (selectionMode) {
                                            toggleFavoriteSelection(favorite.id)
                                        } else {
                                            onOpenVideo(favorite)
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selectedIds = listOf(favorite.id)
                                        } else {
                                            toggleFavoriteSelection(favorite.id)
                                        }
                                    },
                                    selectionMode = selectionMode,
                                    selected = favorite.id in selectedIds,
                                    onToggleSelect = { toggleFavoriteSelection(favorite.id) },
                                    onDelete = { pendingDelete = favorite },
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectionMode,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { it } + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { it } + fadeOut(animationSpec = tween(300)),
            ) {
                LocalFavoriteBatchActionBar(
                    selectedCount = selectedIds.size,
                    totalCount = filteredFavorites.size,
                    onToggleSelectAll = {
                        selectedIds = if (selectedIds.size == filteredFavorites.size) {
                            emptyList()
                        } else {
                            filteredFavorites.map { it.id }
                        }
                    },
                    onExitMultiSelect = {
                        selectionMode = false
                        selectedIds = emptyList()
                    },
                    onMoveToGroup = { showMoveGroupDialog = true },
                    onDeleteSelected = { showBatchDeleteDialog = true },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteCard(
    favorite: FavoriteEntity,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val fixTimestamp = { ts: Long -> if (ts < 9999999999L) ts * 1000 else ts }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val savedDate =
        remember(favorite.savedAt) { dateFormatter.format(Date(fixTimestamp(favorite.savedAt))) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelect() else onClick()
                },
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectionMode) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_check_circle_24),
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.CenterVertically),
                )
            }

            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = favorite.coverUrl,
                    contentDescription = favorite.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = favorite.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!selectionMode) {
                        FilledIconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(25.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.delete_fav),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                FavoriteMeta(
                    iconRes = R.drawable.baseline_bookmark_24,
                    label = stringResource(R.string.local_favorite_saved_at, savedDate),
                )
            }
        }
    }
}

@Composable
private fun FavoriteMeta(
    iconRes: Int,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 本地收藏多選批量操作欄
 */
@Composable
private fun LocalFavoriteBatchActionBar(
    selectedCount: Int,
    totalCount: Int,
    onToggleSelectAll: () -> Unit,
    onExitMultiSelect: () -> Unit,
    onMoveToGroup: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAllSelected = selectedCount == totalCount
    val hasSelection = selectedCount > 0

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onExitMultiSelect) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$selectedCount/$totalCount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleSelectAll) {
                    Text(
                        text = if (isAllSelected) {
                            stringResource(R.string.deselect_all)
                        } else {
                            stringResource(R.string.select_all)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                TextButton(
                    onClick = onMoveToGroup,
                    enabled = hasSelection,
                ) {
                    Text(
                        text = stringResource(R.string.local_favorite_move_to_group),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                TextButton(
                    onClick = onDeleteSelected,
                    enabled = hasSelection,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.delete),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * 收藏分组管理对话框：新建 / 重命名 / 删除分组。
 *
 * @param visible 是否可见
 * @param groups 当前所有分组
 * @param onDismiss 关闭回调
 * @param onCreate 新建分组回调
 * @param onRename 重命名分组回调
 * @param onDelete 删除分组回调（组内收藏自动移入未分组）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteGroupManageDialog(
    visible: Boolean,
    groups: List<FavoriteGroupEntity>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (FavoriteGroupEntity, String) -> Unit,
    onDelete: (FavoriteGroupEntity) -> Unit,
) {
    if (!visible) return
    var name by remember { mutableStateOf("") }
    var pendingRename by remember { mutableStateOf<FavoriteGroupEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<FavoriteGroupEntity?>(null) }

    if (pendingDelete != null) {
        ConfirmDialog(
            visible = true,
            title = stringResource(R.string.delete_group),
            message = stringResource(
                R.string.local_favorite_group_delete_confirm,
                pendingDelete!!.name
            ),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                pendingDelete?.let(onDelete)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(
                        if (pendingRename != null) R.string.rename_group
                        else R.string.local_favorite_group_manage
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                if (pendingRename == null && groups.isNotEmpty()) {
                    val scrollState = rememberLazyListState()
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 240.dp)
                            .verticalScrollbar(
                                state = scrollState,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                width = 4.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(groups, key = { it.id }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                FilledTonalIconButton(
                                    onClick = {
                                        pendingRename = group
                                        name = group.name
                                    },
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.rename_group),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = { pendingDelete = group },
                                    modifier = Modifier.size(30.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.delete_group),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.new_group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val trimmed = name.trim()
                        if (trimmed.isNotBlank()) {
                            if (pendingRename != null) onRename(pendingRename!!, trimmed)
                            else onCreate(trimmed)
                            pendingRename = null
                            name = ""
                        }
                    }),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotBlank()) {
                            if (pendingRename != null) onRename(pendingRename!!, trimmed)
                            else onCreate(trimmed)
                            pendingRename = null
                            name = ""
                        }
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}

/**
 * 批量移动收藏到分组对话框。
 *
 * @param visible 是否可见
 * @param groups 当前所有分组
 * @param onDismiss 关闭回调
 * @param onConfirm 确认移动回调，groupId 为 null 表示未分组
 * @param onCreateAndMove 新建分组并移动回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveFavoriteGroupDialog(
    visible: Boolean,
    groups: List<FavoriteGroupEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
    onCreateAndMove: (String) -> Unit,
) {
    if (!visible) return
    var selectedGroupId by remember { mutableStateOf<Int?>(null) }
    var creatingNew by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.local_favorite_move_to_group),
                    style = MaterialTheme.typography.titleLarge
                )
                if (!creatingNew) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGroupId = null },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedGroupId == null, onClick = null)
                        Text(
                            text = stringResource(R.string.local_favorite_group_ungrouped),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    groups.forEach { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGroupId = group.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedGroupId == group.id, onClick = null)
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    TextButton(onClick = { creatingNew = true }) {
                        Text(stringResource(R.string.local_favorite_group_new))
                    }
                } else {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text(stringResource(R.string.new_group_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val trimmed = newGroupName.trim()
                            if (trimmed.isNotBlank()) {
                                onCreateAndMove(trimmed)
                                creatingNew = false
                                newGroupName = ""
                            }
                        }),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    if (!creatingNew) {
                        TextButton(onClick = { onConfirm(selectedGroupId) }) {
                            Text(stringResource(R.string.confirm))
                        }
                    } else {
                        TextButton(onClick = {
                            val trimmed = newGroupName.trim()
                            if (trimmed.isNotBlank()) {
                                onCreateAndMove(trimmed)
                                creatingNew = false
                                newGroupName = ""
                            }
                        }) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 760)
@Composable
private fun LocalFavoriteScreenPreview() {
    ComponentPreview {
        LocalFavoriteScreen(
            favoritesFlow = kotlinx.coroutines.flow.flowOf(
                listOf(
                    FavoriteEntity(
                        videoCode = "abc123",
                        title = "[中字] 示例影片標題範例",
                        coverUrl = "",
                        releaseDate = System.currentTimeMillis(),
                        savedAt = System.currentTimeMillis(),
                    ),
                    FavoriteEntity(
                        videoCode = "def456",
                        title = "[無碼] 另一個示例影片",
                        coverUrl = "",
                        releaseDate = System.currentTimeMillis(),
                        savedAt = System.currentTimeMillis() - 3_600_000L,
                    ),
                )
            ),
            groupsFlow = kotlinx.coroutines.flow.flowOf(emptyList()),
            onBack = {},
            onOpenVideo = {},
            onDeleteFavorite = {},
            onDeleteAllFavorites = {},
            onCreateGroup = { 0 },
            onRenameGroup = { _, _ -> },
            onDeleteGroup = {},
            onMoveFavoritesToGroup = { _, _ -> },
            onOpenPosterWall = {},
        )
    }
}
