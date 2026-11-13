package com.yenaly.han1meviewer.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yenaly.han1meviewer.logic.state.PageState
import com.yenaly.han1meviewer.ui.component.content.EmptyContent
import com.yenaly.han1meviewer.ui.component.content.ErrorContent
import com.yenaly.han1meviewer.ui.component.content.LoadingContent

@Composable
fun PageContent(
    isLoading: Boolean,
    isError: Boolean,
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
    loadingMessage: String = "",
    errorMessage: String = "",
    onRetry: () -> Unit = {},
    loading: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingContent(message = loadingMessage)
        }
    },
    error: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorContent(message = errorMessage, onRetry = onRetry)
        }
    },
    empty: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyContent(hint = "")
        }
    },
    content: @Composable () -> Unit,
) {
    val targetState = when {
        isError -> PageContentState.Error
        isLoading -> PageContentState.Loading
        isEmpty -> PageContentState.Empty
        else -> PageContentState.Content
    }
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 24 }) togetherWith
                (fadeOut(tween(160)) + slideOutVertically(tween(180)) { -it / 30 })
        },
        contentKey = { it },
        label = "page-content-transition",
    ) { state ->
        when (state) {
            PageContentState.Error -> error()
            PageContentState.Loading -> loading()
            PageContentState.Empty -> empty()
            PageContentState.Content -> content()
        }
    }
}

private enum class PageContentState {
    Loading,
    Error,
    Empty,
    Content,
}

val <T> PageState<T>.isFirstPageLoading: Boolean
    get() = this is PageState.Loading

val <T> PageState<T>.isFirstPageError: Boolean
    get() = this is PageState.Error && cachedInfo == null

val <T> PageState<T>.isFirstPageEmpty: Boolean
    get() = this is PageState.Empty
