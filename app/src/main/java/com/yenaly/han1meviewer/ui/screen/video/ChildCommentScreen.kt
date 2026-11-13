package com.yenaly.han1meviewer.ui.screen.video

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.model.ReportReason
import com.yenaly.han1meviewer.logic.model.VideoCommentArgs
import com.yenaly.han1meviewer.logic.model.VideoComments
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.ui.component.BottomNavAwareSnackbarHost
import com.yenaly.han1meviewer.ui.component.CommentReplyBar
import com.yenaly.han1meviewer.ui.component.CommentReportDialog
import com.yenaly.han1meviewer.ui.component.PageContent
import com.yenaly.han1meviewer.ui.component.VideoCommentCard
import com.yenaly.han1meviewer.ui.component.bottomNavReservedSpacing
import com.yenaly.han1meviewer.ui.component.content.EmptyContent
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn
import com.yenaly.han1meviewer.ui.preview.ComponentPreview
import com.yenaly.han1meviewer.ui.preview.fakeCommentList
import com.yenaly.han1meviewer.ui.screen.rememberRandomLoadingHint
import com.yenaly.han1meviewer.util.isCommentBlocked
import com.yenaly.han1meviewer.util.parseTimeStrToMinutes
import com.yenaly.han1meviewer.util.safeSortedBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildCommentScreen(
    commentsFlow: StateFlow<List<VideoComments.VideoComment>>,
    commentStateFlow: StateFlow<WebsiteState<VideoComments>>,
    reportMessageFlow: Flow<CommentMessage>,
    postReplyStateFlow: Flow<WebsiteState<Unit>>,
    commentLikeStateFlow: Flow<WebsiteState<VideoCommentArgs>>,
    reportReasons: List<ReportReason>,
    isAlreadyLogin: Boolean,
    onRefresh: () -> Unit,
    onReply: (VideoComments.VideoComment, String) -> Unit,
    onReport: (VideoComments.VideoComment, ReportReason) -> Unit,
    onThumbUp: (VideoComments.VideoComment) -> Unit,
    onThumbDown: (VideoComments.VideoComment) -> Unit,
    onCommentLikeSuccess: (VideoCommentArgs) -> Unit,
    keyboardBottomInsetPx: Int = 0,
    onReplyStateChange: (Boolean) -> Unit = {},
) {
    val comments by commentsFlow.collectAsStateWithLifecycle()
    val state by commentStateFlow.collectAsStateWithLifecycle()
    val containerSize = LocalWindowInfo.current.containerSize
    val maxScreenWidth = containerSize.width.dp

    var replyingComment by remember { mutableStateOf<VideoComments.VideoComment?>(null) }
    var replyText by remember { mutableStateOf(TextFieldValue("")) }
    // 回复栏是否展开。和 replyingComment 分开存：关掉回复栏时保留 replyingComment + replyText 作为草稿。
    var showReplyBar by rememberSaveable { mutableStateOf(false) }
    var reportComment by remember { mutableStateOf<VideoComments.VideoComment?>(null) }
    var selectedReasonIndex by remember { mutableIntStateOf(-1) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeBottomInsetPx = WindowInsets.ime.getBottom(density)
    val sharedImeBottomInsetPx = ImeInsetState.keyboardBottomInsetPx.intValue
    val effectiveKeyboardBottomInsetPx = if (sharedImeBottomInsetPx > 0) {
        sharedImeBottomInsetPx
    } else {
        imeBottomInsetPx
    }

    val loginFirstText = stringResource(R.string.login_first)
    val sendFailedText = stringResource(R.string.send_failed)
    val sendSuccessText = stringResource(R.string.send_success)
    val sendingReplyText = stringResource(R.string.sending_reply)
    val commentTooShortText = stringResource(R.string.comment_too_short)

    LaunchedEffect(reportMessageFlow) {
        reportMessageFlow.collect { message ->
            if (message.text.isNotBlank()) {
                snackbarHostState.showSnackbar(message.text)
            }
        }
    }

    LaunchedEffect(postReplyStateFlow) {
        postReplyStateFlow.collect { replyState ->
            when (replyState) {
                is WebsiteState.Error -> snackbarHostState.showSnackbar(sendFailedText)
                WebsiteState.Loading -> snackbarHostState.showSnackbar(sendingReplyText)
                is WebsiteState.Success -> {
                    snackbarHostState.showSnackbar(sendSuccessText)
                    onRefresh()
                }
            }
        }
    }

    LaunchedEffect(commentLikeStateFlow) {
        commentLikeStateFlow.collect { likeState ->
            when (likeState) {
                is WebsiteState.Error -> {
                    snackbarHostState.showSnackbar(likeState.throwable.message ?: "unknown")
                }

                WebsiteState.Loading -> Unit

                is WebsiteState.Success -> onCommentLikeSuccess(likeState.info)
            }
        }
    }

    val commentBlockingEnabled = Preferences.commentKeywordsEnabled
    val blockedCommentKeywords = Preferences.blockedCommentKeywords
    val sortedComments = remember(
        comments,
        commentBlockingEnabled,
        blockedCommentKeywords,
    ) {
        // 评论关键词屏蔽：内容命中关键词的子评论直接隐藏
        comments.filterNot { isCommentBlocked(it.content) }
            .safeSortedBy({ parseTimeStrToMinutes(it.date) }, descending = false)
    }
    val nestedScrollInterop = rememberNestedScrollInteropConnection()

    BackHandler(enabled = showReplyBar) {
        // 只关闭输入栏，保留回复草稿；再次点同一条评论的「回复」可以接着写
        showReplyBar = false
    }

    LaunchedEffect(showReplyBar) {
        if (showReplyBar) {
            onReplyStateChange(true)
        }
    }

    Scaffold(
        modifier = Modifier.widthIn(max = maxScreenWidth),
        snackbarHost = { BottomNavAwareSnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = replyingComment != null && showReplyBar,
                modifier = Modifier.padding(
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
                CommentReplyBar(
                    text = replyText,
                    onTextChange = { replyText = it },
                    onSend = {
                        replyingComment?.let { target ->
                            val prefix = "@${target.username}"
                            val contentLength =
                                replyText.text.trim().removePrefix(prefix).trimStart().length
                            if (contentLength < 5) {
                                scope.launch { snackbarHostState.showSnackbar(commentTooShortText) }
                            } else {
                                onReply(target, replyText.text)
                                replyingComment = null
                                replyText = TextFieldValue("")
                                showReplyBar = false
                            }
                        }
                    },
                    placeholder = stringResource(R.string.reply_child_comment),
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.child_comment),
                style = MaterialTheme.typography.headlineSmall,
            )

            if (sortedComments.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.video_count, sortedComments.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val initialLoading = state is WebsiteState.Loading && sortedComments.isEmpty()
            val initialError = state is WebsiteState.Error && sortedComments.isEmpty()
            val loadingHint = rememberRandomLoadingHint()
            PageContent(
                isLoading = initialLoading,
                isError = initialError,
                isEmpty = sortedComments.isEmpty(),
                errorMessage = (state as? WebsiteState.Error)?.throwable?.message ?: "",
                onRetry = onRefresh,
                loading = {
                    LoadingContent(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        message = loadingHint,
                    )
                },
                error = {
                    ErrorContent(
                        title = stringResource(R.string.load_reply_failed),
                        message = (state as WebsiteState.Error).throwable.message,
                        onRetry = onRefresh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    )
                },
                empty = {
                    EmptyContent(hint = stringResource(R.string.comment_not_found))
                },
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollInterop),
                    // 评论列表禁用入场动画：
                    // item 滚出可视区回收后再滚回会重播动画，造成滚动抽搐
                    enableItemAnimation = false,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sortedComments, key = { it.stableKey }) { comment ->
                        VideoCommentCard(
                            comment = comment,
                            onReply = {
                                if (!isAlreadyLogin) {
                                    scope.launch { snackbarHostState.showSnackbar(loginFirstText) }
                                } else {
                                    // 换了回复目标才重置 "@用户名 " 前缀；同一目标重开时保留草稿
                                    if (replyingComment?.stableKey != comment.stableKey) {
                                        replyText = TextFieldValue("@${comment.username} ")
                                    }
                                    replyingComment = comment
                                    showReplyBar = true
                                }
                            },
                            onThumbUp = {
                                if (!isAlreadyLogin) {
                                    scope.launch { snackbarHostState.showSnackbar(loginFirstText) }
                                } else {
                                    onThumbUp(comment)
                                }
                            },
                            onThumbDown = {
                                if (!isAlreadyLogin) {
                                    scope.launch { snackbarHostState.showSnackbar(loginFirstText) }
                                } else {
                                    onThumbDown(comment)
                                }
                            },
                            onReport = {
                                if (!isAlreadyLogin) {
                                    scope.launch { snackbarHostState.showSnackbar(loginFirstText) }
                                } else {
                                    reportComment = comment
                                }
                            },
                        )
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
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun ChildCommentScreenPreview() {
    ComponentPreview {
        ChildCommentScreen(
            commentsFlow = MutableStateFlow(fakeCommentList),
            commentStateFlow = MutableStateFlow(WebsiteState.Success(VideoComments(fakeCommentList.toMutableList()))),
            reportMessageFlow = flowOf(CommentMessage("")),
            postReplyStateFlow = flowOf(WebsiteState.Success(Unit)),
            commentLikeStateFlow = flowOf(
                WebsiteState.Success(
                    VideoCommentArgs(
                        isPositive = true,
                        commentPosition = 0,
                        comment = fakeCommentList.first(),
                    )
                )
            ),
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
            isAlreadyLogin = true,
            onRefresh = {},
            onReply = { _, _ -> },
            onReport = { _, _ -> },
            onThumbUp = {},
            onThumbDown = {},
            onCommentLikeSuccess = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun ChildCommentScreenEmptyPreview() {
    ComponentPreview {
        ChildCommentScreen(
            commentsFlow = MutableStateFlow(emptyList()),
            commentStateFlow = MutableStateFlow(WebsiteState.Success(VideoComments(mutableListOf()))),
            reportMessageFlow = flowOf(CommentMessage("")),
            postReplyStateFlow = flowOf(WebsiteState.Loading),
            commentLikeStateFlow = flowOf(WebsiteState.Loading),
            reportReasons = emptyList(),
            isAlreadyLogin = true,
            onRefresh = {},
            onReply = { _, _ -> },
            onReport = { _, _ -> },
            onThumbUp = {},
            onThumbDown = {},
            onCommentLikeSuccess = {},
        )
    }
}
