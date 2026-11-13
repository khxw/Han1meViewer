package com.yenaly.han1meviewer.ui.screen.video

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.ReportReason
import com.yenaly.han1meviewer.logic.model.VideoComments
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.navigation.settings.SettingsPreferenceKeys
import com.yenaly.han1meviewer.ui.component.BottomNavAwareSnackbarHost
import com.yenaly.han1meviewer.ui.component.CommentReplyBar
import com.yenaly.han1meviewer.ui.component.CommentReportDialog
import com.yenaly.han1meviewer.ui.component.PageContent
import com.yenaly.han1meviewer.ui.component.VideoCommentCard
import com.yenaly.han1meviewer.ui.component.bottomNavReservedSpacing
import com.yenaly.han1meviewer.ui.component.content.EmptyContent
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn
import com.yenaly.han1meviewer.ui.preview.fakeCommentList
import com.yenaly.han1meviewer.util.isCommentBlocked
import com.yenaly.han1meviewer.util.parseTimeStrToMinutes
import com.yenaly.han1meviewer.util.safeSortedBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun CommentScreen(
    modifier: Modifier = Modifier,
    commentsFlow: StateFlow<List<VideoComments.VideoComment>>,
    commentStateFlow: StateFlow<WebsiteState<VideoComments>>,
    reportMessageFlow: Flow<CommentMessage>,
    currentSortType: StateFlow<CommentSortType>,
    reportReasons: List<ReportReason>,
    isPreviewCommentPrefetched: Boolean,
    isAlreadyLogin: Boolean,
    onRefresh: () -> Unit,
    onReply: (VideoComments.VideoComment, String) -> Unit,
    onReport: (VideoComments.VideoComment, ReportReason) -> Unit,
    onThumbUp: (VideoComments.VideoComment) -> Unit,
    onThumbDown: (VideoComments.VideoComment) -> Unit,
    onViewMoreReplies: (VideoComments.VideoComment) -> Unit,
    onSortChange: (CommentSortType) -> Unit,
    onComposeComment: (String) -> Unit,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    onCommentScrollChange: (Int, Int) -> Unit = { _, _ -> },
    keyboardBottomInsetPx: Int = 0,
    draftKey: String? = null,
) {
    val comments by commentsFlow.collectAsStateWithLifecycle()
    val state by commentStateFlow.collectAsStateWithLifecycle()
    val sortType by currentSortType.collectAsStateWithLifecycle()
    val containerSize = LocalWindowInfo.current.containerSize
    val maxScreenWidth = containerSize.width.dp

    var showKeywordSheet by rememberSaveable { mutableStateOf(false) }
    var replyingComment by remember { mutableStateOf<VideoComments.VideoComment?>(null) }
    var reportComment by remember { mutableStateOf<VideoComments.VideoComment?>(null) }
    var showCommentBar by rememberSaveable { mutableStateOf(false) }
    // 回复栏是否展开。和 replyingComment 分开存：关掉回复栏时保留 replyingComment + replyText 作为草稿。
    var showReplyBar by rememberSaveable { mutableStateOf(false) }
    var blockedCommentKeywords by rememberSaveable {
        mutableStateOf(Preferences.blockedCommentKeywords.toList().sorted())
    }
    var newBlockedCommentKeyword by rememberSaveable { mutableStateOf("") }
    val blockedCommentKeywordSet = remember(blockedCommentKeywords) {
        blockedCommentKeywords.toSet()
    }
    val commentBlockingEnabled = blockedCommentKeywordSet.isNotEmpty()
    val persistCommentBlocking: (List<String>) -> Unit = { keywords ->
        Preferences.preferenceSp.edit {
            putBoolean(SettingsPreferenceKeys.COMMENT_KEYWORDS_ENABLED, keywords.isNotEmpty())
            putString(
                SettingsPreferenceKeys.BLOCKED_COMMENT_KEYWORDS,
                keywords.filter { it.isNotBlank() }.joinToString(","),
            )
        }
    }
    val toggleBlockedCommentKeyword: (String) -> Unit = { rawKeyword ->
        val keyword = rawKeyword.trim()
        if (keyword.isNotEmpty()) {
            val existing = blockedCommentKeywords.firstOrNull {
                it.equals(keyword, ignoreCase = true)
            }
            val nextKeywords = if (existing == null) {
                listOf(keyword) + blockedCommentKeywords
            } else {
                blockedCommentKeywords - existing
            }
            blockedCommentKeywords = nextKeywords
            persistCommentBlocking(nextKeywords)
        }
    }
    var replyText by remember { mutableStateOf(TextFieldValue("")) }
    val draftPreferenceKey = draftKey?.let { "comment_draft_$it" }
    var composeText by remember(draftKey) {
        val initialDraft = draftPreferenceKey
            ?.let { Preferences.preferenceSp.getString(it, "").orEmpty() }
            .orEmpty()
        mutableStateOf(TextFieldValue(initialDraft))
    }
    var selectedReasonIndex by remember { mutableIntStateOf(-1) }
    var latestReportMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshingState = rememberPullToRefreshState()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset,
    )
    val showCommentFab by rememberCommentFabVisibility(listState)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeBottomInsetPx = WindowInsets.ime.getBottom(density)
    val sharedImeBottomInsetPx = ImeInsetState.keyboardBottomInsetPx.intValue
    val effectiveKeyboardBottomInsetPx = if (sharedImeBottomInsetPx > 0) {
        sharedImeBottomInsetPx
    } else {
        imeBottomInsetPx
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val loginFirstText = stringResource(R.string.login_first)
    val commentTooShortText = stringResource(R.string.comment_too_short)
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    DisposableEffect(Unit) {
        onDispose {
            ImeInsetState.keyboardBottomInsetPx.intValue = 0
        }
    }
    LaunchedEffect(reportMessageFlow) {
        reportMessageFlow.collect {
            latestReportMessage = it.text
            if (it.text.isNotBlank()) {
                snackbarHostState.showSnackbar(it.text)
            }
        }
    }

    LaunchedEffect(draftPreferenceKey, composeText.text) {
        val key = draftPreferenceKey ?: return@LaunchedEffect
        delay(500)
        Preferences.preferenceSp.edit {
            if (composeText.text.isBlank()) {
                remove(key)
            } else {
                putString(key, composeText.text)
            }
        }
    }

    val sortedComments = remember(
        comments,
        sortType,
        commentBlockingEnabled,
        blockedCommentKeywordSet,
    ) {
        val filtered = comments.filterNot {
            isCommentBlocked(
                content = it.content,
                enabled = commentBlockingEnabled,
                keywords = blockedCommentKeywordSet,
            )
        }
        sortComments(filtered, sortType)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            onCommentScrollChange(index, offset)
        }
    }

    BackHandler(enabled = showReplyBar || showCommentBar) {
        // 只关闭输入栏，保留草稿：
        // - 主评论草稿由 composeText 自动落盘（见 draftPreferenceKey）
        // - 回复草稿 = replyText + replyingComment，关掉后再次点同一条评论的「回复」可以接着写
        showReplyBar = false
        showCommentBar = false
        keyboardController?.hide()
    }

    if (showKeywordSheet) {
        ModalBottomSheet(onDismissRequest = { showKeywordSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.comment_keyword_blocking),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newBlockedCommentKeyword,
                        onValueChange = { newBlockedCommentKeyword = it },
                        placeholder = { Text(stringResource(R.string.comment_keyword_add_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            toggleBlockedCommentKeyword(newBlockedCommentKeyword)
                            newBlockedCommentKeyword = ""
                        },
                        enabled = newBlockedCommentKeyword.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.add))
                    }
                }
                if (blockedCommentKeywords.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp, max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        blockedCommentKeywords.forEach { keyword ->
                            AssistChip(
                                onClick = { toggleBlockedCommentKeyword(keyword) },
                                label = { Text(keyword) },
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
            }
        }
    }

    if (reportComment != null) {
        CommentReportDialog(
            reportReasons = reportReasons,
            selectedReasonIndex = selectedReasonIndex,
            onSelectReason = { selectedReasonIndex = it },
            onConfirm = {
                val reason = reportReasons.getOrNull(selectedReasonIndex)
                val target = reportComment
                if (reason != null && target != null) {
                    onReport(target, reason)
                }
                reportComment = null
                selectedReasonIndex = -1
            },
            onDismiss = {
                reportComment = null
                selectedReasonIndex = -1
            },
        )
    }

    Scaffold(
        modifier = modifier.widthIn(max = maxScreenWidth),
        snackbarHost = { BottomNavAwareSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isAlreadyLogin) {
                AnimatedVisibility(
                    visible = showCommentFab && !showReplyBar && !showCommentBar,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.comment)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_reply_24),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.padding(bottom = bottomNavReservedSpacing()),
                        onClick = { showCommentBar = true },
                    )
                }
            }
        },
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        end = paddingValues.calculateEndPadding(layoutDirection),
                    )
            ) {
                PullToRefreshBox(
                    isRefreshing = state is WebsiteState.Loading && !isPreviewCommentPrefetched,
                    onRefresh = onRefresh,
                    state = refreshingState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = refreshingState,
                            isRefreshing = state is WebsiteState.Loading && !isPreviewCommentPrefetched,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                ) {
                    val loadError = state is WebsiteState.Error && sortedComments.isEmpty()
                    PageContent(
                        isLoading = false,
                        isError = loadError,
                        isEmpty = sortedComments.isEmpty(),
                        errorMessage = (state as? WebsiteState.Error)?.throwable?.message ?: "",
                        onRetry = onRefresh,
                        error = {
                            ErrorContent(
                                title = stringResource(R.string.load_failed_retry),
                                message = (state as WebsiteState.Error).throwable.message,
                                onRetry = onRefresh,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp),
                            )
                        },
                        empty = {
                            EmptyContent(
                                hint = stringResource(R.string.comment_not_found),
                                subHint = latestReportMessage ?: ""
                            )
                        },
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollInterop),
                            // 评论列表禁用入场动画：
                            // item 滚出可视区回收后再滚回会重播动画，造成滚动抽搐
                            enableItemAnimation = false,
                            contentPadding = PaddingValues(start = 8.dp, top = 56.dp, end = 8.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {

                            items(sortedComments, key = { it.stableKey }) { comment ->
                                VideoCommentCard(
                                    comment = comment,
                                    onReply = {
                                        if (!isAlreadyLogin) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    loginFirstText
                                                )
                                            }
                                        } else {
                                            // 换了回复目标就清空旧草稿，避免把写给别人的内容发错人
                                            if (replyingComment?.stableKey != comment.stableKey) {
                                                replyText = TextFieldValue("")
                                            }
                                            replyingComment = comment
                                            showReplyBar = true
                                        }
                                    },
                                    onThumbUp = {
                                        if (!isAlreadyLogin) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    loginFirstText
                                                )
                                            }
                                        } else {
                                            onThumbUp(comment)
                                        }
                                    },
                                    onThumbDown = {
                                        if (!isAlreadyLogin) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    loginFirstText
                                                )
                                            }
                                        } else {
                                            onThumbDown(comment)
                                        }
                                    },
                                    onReport = {
                                        if (!isAlreadyLogin) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    loginFirstText
                                                )
                                            }
                                        } else {
                                            reportComment = comment
                                        }
                                    },
                                    onViewMoreReplies = if (comment.hasMoreReplies) {
                                        { onViewMoreReplies(comment) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (comments.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { showKeywordSheet = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 12.dp),
                ) {
                    Text(stringResource(R.string.comment_keyword_blocking))
                }
            }
            AnimatedVisibility(
                visible = showCommentBar || (replyingComment != null && showReplyBar),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = if (effectiveKeyboardBottomInsetPx > 0) {
                            with(LocalDensity.current) {
                                effectiveKeyboardBottomInsetPx.toDp()
                            }
                        } else {
                            bottomNavReservedSpacing()
                        },
                    ),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                if (replyingComment != null) {
                    CommentReplyBar(
                        text = replyText,
                        onTextChange = { replyText = it },
                        onSend = {
                            val text = replyText.text.trim()
                            if (text.length < 5) {
                                scope.launch { snackbarHostState.showSnackbar(commentTooShortText) }
                            } else {
                                replyingComment?.let { onReply(it, replyText.text) }
                                replyingComment = null
                                replyText = TextFieldValue("")
                                showReplyBar = false
                                keyboardController?.hide()
                            }
                        },
                        placeholder = stringResource(R.string.comment),
                    )
                } else {
                    CommentReplyBar(
                        text = composeText,
                        onTextChange = { composeText = it },
                        onSend = {
                            val text = composeText.text.trim()
                            if (text.length < 5) {
                                scope.launch { snackbarHostState.showSnackbar(commentTooShortText) }
                            } else {
                                onComposeComment(text)
                                draftPreferenceKey?.let { key ->
                                    Preferences.preferenceSp.edit { remove(key) }
                                }
                                showCommentBar = false
                                composeText = TextFieldValue("")
                                keyboardController?.hide()
                            }
                        },
                        placeholder = stringResource(R.string.comment),
                    )
                }
            }
        }
    }
}

private fun sortComments(
    list: List<VideoComments.VideoComment>,
    type: CommentSortType,
): List<VideoComments.VideoComment> = when (type) {
    CommentSortType.LATEST -> list.safeSortedBy(
        { parseTimeStrToMinutes(it.date) },
        descending = false
    )

    CommentSortType.EARLIEST -> list.safeSortedBy(
        { parseTimeStrToMinutes(it.date) },
        descending = true
    )

    CommentSortType.MOST_REPLY -> list.safeSortedBy({ it.replyCount ?: 0 }, descending = true)
    CommentSortType.MOST_LIKES -> list.safeSortedBy({ it.realLikesCount ?: 0 }, descending = true)
    CommentSortType.MOST_DISLIKES -> list.safeSortedBy(
        { it.realLikesCount ?: 0 },
        descending = false
    )
}

@Composable
private fun sortText(type: CommentSortType): String = when (type) {
    CommentSortType.LATEST -> stringResource(R.string.sort_by_newest)
    CommentSortType.EARLIEST -> stringResource(R.string.sort_by_oldest)
    CommentSortType.MOST_REPLY -> stringResource(R.string.sort_by_replies)
    CommentSortType.MOST_LIKES -> stringResource(R.string.sort_most_likes)
    CommentSortType.MOST_DISLIKES -> stringResource(R.string.sort_most_dislikes)
}

data class CommentMessage(val text: String)

@Composable
private fun rememberCommentFabVisibility(listState: LazyListState): androidx.compose.runtime.State<Boolean> {
    return remember(listState) {
        derivedStateOf {
            val scrollOffset = listState.firstVisibleItemScrollOffset
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val lastScrolledBackward = listState.lastScrolledBackward
            val lastScrolledForward = listState.lastScrolledForward

            when {
                firstVisibleItemIndex == 0 && scrollOffset == 0 -> true
                lastScrolledBackward -> true
                lastScrolledForward -> false
                else -> true
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun CommentScreenPreview() {
    CommentScreen(
        commentsFlow = MutableStateFlow(fakeCommentList),
        commentStateFlow = MutableStateFlow(WebsiteState.Success(VideoComments(fakeCommentList.toMutableList()))),
        reportMessageFlow = flowOf(CommentMessage("")),
        currentSortType = MutableStateFlow(CommentSortType.LATEST),
        reportReasons = listOf(
            ReportReason(
                lang = ReportReason.Language(
                    zhrTW = "垃圾訊息",
                    zhrCN = "垃圾信息",
                    en = "Spam",
                ),
                reasonKey = "spam",
            )
        ),
        isPreviewCommentPrefetched = false,
        isAlreadyLogin = true,
        onRefresh = {},
        onReply = { _, _ -> },
        onReport = { _, _ -> },
        onThumbUp = {},
        onThumbDown = {},
        onViewMoreReplies = {},
        onSortChange = {},
        onComposeComment = {},
    )
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun CommentScreenEmptyPreview() {
    CommentScreen(
        commentsFlow = MutableStateFlow(emptyList()),
        commentStateFlow = MutableStateFlow(WebsiteState.Success(VideoComments(fakeCommentList.toMutableList()))),
        reportMessageFlow = flowOf(CommentMessage("")),
        currentSortType = MutableStateFlow(CommentSortType.LATEST),
        reportReasons = emptyList(),
        isPreviewCommentPrefetched = false,
        isAlreadyLogin = true,
        onRefresh = {},
        onReply = { _, _ -> },
        onReport = { _, _ -> },
        onThumbUp = {},
        onThumbDown = {},
        onViewMoreReplies = {},
        onSortChange = {},
        onComposeComment = {},
    )
}
