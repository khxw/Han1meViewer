package com.yenaly.han1meviewer.ui.screen.video

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import com.google.android.material.color.MaterialColors
import com.yenaly.han1meviewer.HAdvancedSearch
import com.yenaly.han1meviewer.HCacheManager
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.getHanimeVideoDownloadLink
import com.yenaly.han1meviewer.getHanimeVideoLink
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.entity.FavoriteEntity
import com.yenaly.han1meviewer.logic.entity.FavoriteGroupEntity
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import com.yenaly.han1meviewer.logic.model.SearchOption
import com.yenaly.han1meviewer.ui.activity.MainActivity
import com.yenaly.han1meviewer.ui.navigation.navigateSafely
import com.yenaly.han1meviewer.ui.navigation.main.SearchRoute
import com.yenaly.han1meviewer.ui.viewmodel.VideoViewModel
import com.yenaly.han1meviewer.util.createAlertDialog
import com.yenaly.han1meviewer.util.requestPostNotificationPermission
import com.yenaly.han1meviewer.util.showAlertDialog
import com.yenaly.han1meviewer.util.showWithBlurEffect
import com.yenaly.han1meviewer.worker.HanimeDownloadManagerV2
import com.yenaly.han1meviewer.worker.HanimeDownloadWorker
import com.yenaly.yenaly_libs.utils.browse
import com.yenaly.yenaly_libs.utils.copyToClipboard
import com.yenaly.yenaly_libs.utils.dp
import com.yenaly.yenaly_libs.utils.dpF
import com.yenaly.yenaly_libs.utils.showShortToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.Serializable

class VideoRouteActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val viewModel: VideoViewModel,
    private val genres: List<SearchOption>,
    private val requestStoragePermission: (
        onGranted: () -> Unit,
        onDenied: () -> Unit,
        onPermanentlyDenied: () -> Unit,
    ) -> Unit,
    private val onPendingDownloadPromptChange: (DownloadPromptState?) -> Unit,
    private val getCheckedQuality: () -> String?,
    private val setCheckedQuality: (String?) -> Unit,
    private val onStoragePermissionDenied: () -> Unit = {},
    private val onDownloadPermissionDialogCancelled: () -> Unit = {},
) {
    fun openArtistSearch(artist: HanimeVideo.Artist) {
        val searchKey = genres.firstOrNull { option ->
            option.lang?.let { lang ->
                artist.genre == lang.zhrCN ||
                        artist.genre == lang.zhrTW ||
                        artist.genre == lang.en
            } == true
        }?.searchKey ?: ""
        val map = buildMap<HAdvancedSearch, Serializable> {
            put(HAdvancedSearch.QUERY, artist.name)
            if (searchKey.isNotEmpty() && !Preferences.searchArtistIgnoreVideoType) {
                put(HAdvancedSearch.GENRE, searchKey)
            }
        }
        val bundleMap = HashMap<String, Serializable>().apply {
            map.forEach { (key, value) -> put(key.name, value) }
        }
        val routeMap = bundleMap.mapValues { it.value.toString() }
        (context as? MainActivity)?.navController?.navigateSafely(
            SearchRoute(query = artist.name, advancedSearchJson = Json.encodeToString(routeMap))
        )
    }

    fun openTagSearch(tag: String) {
        (context as? MainActivity)?.navController?.navigateSafely(SearchRoute(query = tag))
    }

    fun toggleArtistSubscription(artist: HanimeVideo.Artist) {
        val post = artist.post ?: return
        if (!Preferences.isAlreadyLogin) {
            showShortToast(R.string.login_first)
            return
        }
        if (artist.isSubscribed) {
            context.showAlertDialog {
                setTitle(R.string.unsubscribe_artist)
                setMessage(R.string.sure_to_unsubscribe)
                setPositiveButton(R.string.sure) { _, _ ->
                    viewModel.unsubscribeArtist(post.userId, post.artistId)
                }
                setNegativeButton(R.string.no, null)
            }
        } else {
            viewModel.subscribeArtist(post.userId, post.artistId)
        }
    }

    fun toggleFavorite(video: HanimeVideo) {
        if (!Preferences.isAlreadyLogin) {
            showShortToast(R.string.login_first)
            return
        }
        if (video.isFav) {
            viewModel.removeFromFavVideo(viewModel.videoCode, video.currentUserId)
        } else {
            viewModel.addToFavVideo(viewModel.videoCode, video.currentUserId)
        }
    }

    /**
     * 切换本地收藏（离线收藏），不依赖 Hanime 账号。
     *
     * 未收藏时弹出分组选择对话框，选择分组后收藏；已收藏时直接取消收藏。
     */
    fun toggleLocalFavorite(video: HanimeVideo) {
        val videoCode = viewModel.videoCode
        scope.launch {
            if (DatabaseRepo.Favorite.findBy(videoCode) != null) {
                DatabaseRepo.Favorite.delete(videoCode)
            } else {
                val groups = DatabaseRepo.FavoriteGroup.getAll()
                showLocalFavoriteGroupPicker(video, groups)
            }
        }
    }

    /**
     * 收藏分组选择对话框：单列圆角卡片（带图标）。
     *
     * 「未分组 + 各分组」组成可滚动列表（超过 5 项时内部滚动），
     * 「新建分组」固定在底部，不随列表滚动。
     */
    private fun showLocalFavoriteGroupPicker(
        video: HanimeVideo,
        groups: List<FavoriteGroupEntity>,
    ) {
        val labels = listOf(context.getString(R.string.local_favorite_group_ungrouped)) +
                groups.map { it.name }

        lateinit var dialog: AlertDialog
        val groupList = createLocalFavoriteGroupList(
            context = context,
            labels = labels,
            newGroupLabel = context.getString(R.string.local_favorite_group_new),
            onSelect = { which ->
                dialog.dismiss()
                if (which == 0) {
                    scope.launch { insertLocalFavorite(video, null) }
                } else {
                    scope.launch { insertLocalFavorite(video, groups[which - 1].id) }
                }
            },
            onCreateNewGroup = {
                dialog.dismiss()
                showNewGroupDialog(video, groups)
            },
        )
        dialog = context.createAlertDialog {
            setTitle(R.string.local_favorite_select_group)
            setView(groupList)
            setNegativeButton(R.string.cancel, null)
        }
        dialog.showWithBlurEffect()
    }

    /**
     * 新建分组输入对话框。
     *
     * 相比旧实现：输入框带内边距、自动聚焦并拉起键盘、支持回车提交；
     * 名称为空或与已有分组重名时确认按钮不可用，重名时输入框显示错误提示。
     */
    private fun showNewGroupDialog(video: HanimeVideo, groups: List<FavoriteGroupEntity>) {
        val input = EditText(context).apply {
            hint = context.getString(R.string.local_favorite_group_name_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val inputCard = FrameLayout(context).apply {
            background = roundedCardBackground(
                context,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
            )
            setPadding(16.dp, 4.dp, 16.dp, 4.dp)
        }
        input.background = null
        input.setPadding(0, 12.dp, 0, 12.dp)
        inputCard.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val container = FrameLayout(context).apply {
            setPadding(24.dp, 8.dp, 24.dp, 0)
        }
        container.addView(
            inputCard,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        fun isDuplicated(name: String) = groups.any { it.name.equals(name, ignoreCase = true) }

        val dialog = context.createAlertDialog {
            setTitle(R.string.local_favorite_group_new)
            setView(container)
            setPositiveButton(R.string.confirm, null)
            setNegativeButton(R.string.cancel, null)
        }

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                isEnabled = false
            }
            fun refreshState() {
                val name = input.text.toString().trim()
                val duplicated = name.isNotEmpty() && isDuplicated(name)
                input.error = if (duplicated) {
                    context.getString(R.string.local_favorite_group_duplicated)
                } else {
                    null
                }
                positiveButton.isEnabled = name.isNotEmpty() && !duplicated
            }
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) = refreshState()
            })
            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    if (positiveButton.isEnabled) positiveButton.performClick()
                    true
                } else {
                    false
                }
            }
            positiveButton.setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty() || isDuplicated(name)) return@setOnClickListener
                dialog.dismiss()
                scope.launch {
                    val groupId = DatabaseRepo.FavoriteGroup.insert(name)
                    insertLocalFavorite(video, groupId)
                }
            }
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.showWithBlurEffect()
    }

    private suspend fun insertLocalFavorite(video: HanimeVideo, groupId: Int?) {
        DatabaseRepo.Favorite.insert(
            FavoriteEntity(
                videoCode = viewModel.videoCode,
                title = video.title,
                coverUrl = video.coverUrl,
                releaseDate = video.uploadTime?.toEpochDays()?.times(86_400_000L) ?: 0L,
                savedAt = System.currentTimeMillis(),
                groupId = groupId,
            )
        )
    }

    fun rateVideo(video: HanimeVideo, isPositive: Boolean) {
        if (!Preferences.isAlreadyLogin) {
            showShortToast(R.string.login_first)
            return
        }
        viewModel.rateVideo(video, isPositive)
    }

    fun updateMyListSelection(
        myList: HanimeVideo.MyList?,
        selectedStates: List<Boolean>,
    ) {
        if (!Preferences.isAlreadyLogin || myList == null || myList.myListInfo.isEmpty()) {
            showShortToast(R.string.login_first)
            return
        }
        myList.myListInfo.forEachIndexed { index, info ->
            val newChecked = selectedStates.getOrNull(index) ?: return@forEachIndexed
            if (info.isSelected != newChecked) {
                viewModel.modifyMyList(
                    listCode = info.code,
                    videoCode = viewModel.videoCode,
                    isChecked = newChecked,
                    position = index,
                )
            }
        }
    }

    fun openIntroductionLink(link: String) {
        try {
            context.browse(link)
        } catch (_: Exception) {
            link.copyToClipboard()
            showShortToast(R.string.copy_to_clipboard)
        }
    }

    fun openOriginalComic(comicLink: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, comicLink.toUri()))
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.fault_prompt), Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun openVideoWebPage() {
        context.browse(getHanimeVideoLink(viewModel.videoCode))
    }

    fun openOfficialDownloadPage() {
        context.browse(getHanimeVideoDownloadLink(viewModel.videoCode))
    }

    fun startDownloadFlow(videoData: HanimeVideo) {
        if (videoData.videoUrls.isEmpty()) {
            showShortToast(R.string.no_video_links_found)
            return
        }
        requestStoragePermission(
            {
                viewModel.findDownloadedHanime(viewModel.videoCode)
            },
            {
                Toast.makeText(
                    context,
                    R.string.storage_permission_denied_toast,
                    Toast.LENGTH_LONG,
                ).show()
                onStoragePermissionDenied()
            },
            { openDownloadPermissionSettings() },
        )
    }

    fun confirmPendingDownload(
        videoData: HanimeVideo,
        pendingDownloadPrompt: DownloadPromptState?
    ) {
        val redownload = pendingDownloadPrompt?.oldQuality != null
        onPendingDownloadPromptChange(null)
        scope.launch {
            enqueueDownloadWork(videoData, redownload = redownload)
        }
    }

    private suspend fun enqueueDownloadWork(videoData: HanimeVideo, redownload: Boolean = false) {
        context.requestPostNotificationPermission()
        val quality = getCheckedQuality()
        withContext(Dispatchers.IO) {
            HCacheManager.savePendingDownloadInfo(context, viewModel.videoCode, videoData)
        }
        HanimeDownloadManagerV2.addTask(
            HanimeDownloadWorker.Args(
                quality = quality,
                downloadUrl = videoData.videoUrls[quality]?.link,
                videoType = videoData.videoUrls[quality]?.suffix,
                hanimeName = videoData.title,
                videoCode = viewModel.videoCode,
                coverUrl = videoData.coverUrl,
            ),
            redownload = redownload,
        )
    }

    fun openDownloadPermissionSettings() {
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.permission_permanently_denied_title)
            .setMessage(R.string.storage_permission_settings_message)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                })
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                onDownloadPermissionDialogCancelled()
            }
            .show()
    }
}

/** 读取主题色，取不到时回退到品牌色。 */
private fun themeColor(context: Context, colorAttr: Int): Int =
    MaterialColors.getColor(context, colorAttr, context.getColor(R.color.per90_dark_red))

/** 用主题色生成圆角卡片背景，统一弹窗内的卡片样式。 */
private fun roundedCardBackground(
    context: Context,
    colorAttr: Int,
    radiusDp: Number = 14,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = radiusDp.dpF
    setColor(themeColor(context, colorAttr))
}

/** 本地收藏分组列表最多显示的行数（不含固定的「新建分组」），超过后内部滚动。 */
private const val LOCAL_FAVORITE_MAX_VISIBLE_ROWS = 5

/**
 * 生成本地收藏分组选择弹窗的内容。
 *
 * [labels]（未分组 + 各分组）超过 [LOCAL_FAVORITE_MAX_VISIBLE_ROWS] 项时限制高度并内部滚动；
 * [newGroupLabel] 对应的「新建分组」行始终固定在列表下方。
 */
private fun createLocalFavoriteGroupList(
    context: Context,
    labels: List<String>,
    newGroupLabel: String,
    onSelect: (Int) -> Unit,
    onCreateNewGroup: () -> Unit,
): View {
    val rowGap = 8.dp

    fun buildRow(label: String, accent: Boolean, iconRes: Int, onClick: () -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 48.dp
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
            background = roundedCardBackground(
                context,
                if (accent) {
                    com.google.android.material.R.attr.colorPrimaryContainer
                } else {
                    com.google.android.material.R.attr.colorSurfaceContainerHigh
                },
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        row.addView(
            ImageView(context).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(22.dp, 22.dp)
            },
        )
        row.addView(
            TextView(context).apply {
                text = label
                textSize = 15f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(
                    themeColor(
                        context,
                        if (accent) {
                            com.google.android.material.R.attr.colorOnPrimaryContainer
                        } else {
                            com.google.android.material.R.attr.colorOnSurface
                        },
                    ),
                )
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply { marginStart = 12.dp }
            },
        )
        return row
    }

    fun LinearLayout.addRow(row: View) {
        addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = rowGap },
        )
    }

    val listColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    labels.forEachIndexed { index, label ->
        listColumn.addRow(
            buildRow(
                label = label,
                accent = false,
                iconRes = if (index == 0) {
                    R.drawable.baseline_bookmark_border_24
                } else {
                    R.drawable.baseline_bookmark_24
                },
                onClick = { onSelect(index) },
            ),
        )
    }

    val listView: View = if (labels.size > LOCAL_FAVORITE_MAX_VISIBLE_ROWS) {
        val maxHeightPx = LOCAL_FAVORITE_MAX_VISIBLE_ROWS * (48.dp + rowGap)
        MaxHeightScrollView(context, maxHeightPx).apply {
            addView(
                listColumn,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    } else {
        listColumn
    }

    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(12.dp, 10.dp, 12.dp, 2.dp)
        addView(
            listView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        // 固定的「新建分组」，不参与上面的滚动
        addRow(
            buildRow(
                label = newGroupLabel,
                accent = true,
                iconRes = R.drawable.baseline_add_24,
                onClick = onCreateNewGroup,
            ),
        )
    }
}

/** 限制最大高度的 ScrollView：内容超过 maxHeightPx 时改为内部滚动。 */
private class MaxHeightScrollView(
    context: Context,
    private val maxHeightPx: Int,
) : ScrollView(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val size = View.MeasureSpec.getSize(heightMeasureSpec)
        val cappedSpec = if (mode == View.MeasureSpec.UNSPECIFIED) {
            View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST)
        } else {
            View.MeasureSpec.makeMeasureSpec(minOf(size, maxHeightPx), View.MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, cappedSpec)
    }
}
