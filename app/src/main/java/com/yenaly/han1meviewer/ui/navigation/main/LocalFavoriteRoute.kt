package com.yenaly.han1meviewer.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.ui.screen.home.LocalFavoriteScreen
import kotlinx.coroutines.launch

/**
 * 本地收藏（离线收藏）路由
 *
 * @author Yenaly Liew
 */
@Composable
fun LocalFavoriteRouteScreen(
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onOpenPosterWall: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    LocalFavoriteScreen(
        favoritesFlow = DatabaseRepo.Favorite.loadAll(),
        groupsFlow = DatabaseRepo.FavoriteGroup.loadAll(),
        onBack = onBack,
        onOpenVideo = { onNavigateToVideo(it.videoCode) },
        onDeleteFavorite = { favorite ->
            scope.launch { DatabaseRepo.Favorite.delete(favorite) }
        },
        onDeleteAllFavorites = {
            scope.launch { DatabaseRepo.Favorite.deleteAll() }
        },
        onCreateGroup = { name -> DatabaseRepo.FavoriteGroup.insert(name) },
        onRenameGroup = { group, name ->
            scope.launch { DatabaseRepo.FavoriteGroup.rename(group, name) }
        },
        onDeleteGroup = { group ->
            scope.launch { DatabaseRepo.FavoriteGroup.deleteWithFavorites(group) }
        },
        onMoveFavoritesToGroup = { ids, groupId ->
            scope.launch { DatabaseRepo.Favorite.moveToGroup(ids, groupId) }
        },
        onOpenPosterWall = onOpenPosterWall,
    )
}
