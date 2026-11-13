package com.yenaly.han1meviewer.ui.screen.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.entity.FavoriteEntity
import com.yenaly.han1meviewer.ui.component.appbar.HanimeScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private fun FavoriteEntity.resolvedCoverUrl(): String {
    val raw = coverUrl.trim()
    return when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> Preferences.baseUrl.trimEnd('/') + raw
        else -> raw
    }
}

@Composable
private fun PosterCoverFallback(title: String, modifier: Modifier) {
    Box(
        modifier = modifier.background(Color(0xFF2A1A20)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.take(1),
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White.copy(alpha = 0.82f),
            fontWeight = FontWeight.Bold,
        )
    }
}

private const val PosterColumns = 3
private const val PosterRows = 4
private const val PosterItemCount = PosterColumns * PosterRows

@Composable
fun FavoritePosterRouteScreen(onBack: () -> Unit) {
    val favorites by DatabaseRepo.Favorite.loadAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(favorites) {
        favorites.forEach { favorite ->
            val resolved = favorite.resolvedCoverUrl()
            if (resolved.isNotBlank() && resolved != favorite.coverUrl) {
                DatabaseRepo.Favorite.update(favorite.copy(coverUrl = resolved))
            }
        }


    }
    FavoritePosterScreen(favorites = favorites, onBack = onBack)
}

@Composable
private fun FavoritePosterScreen(
    favorites: List<FavoriteEntity>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shuffleSeed by rememberSaveable { mutableStateOf(0) }
    var exporting by remember { mutableStateOf(false) }

    val posterItems = remember(favorites, shuffleSeed) {
        val sorted = favorites.sortedByDescending { it.savedAt }
        if (sorted.size <= PosterItemCount) {
            sorted
        } else {
            sorted.shuffled(Random(shuffleSeed.toLong())).take(PosterItemCount)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = runCatching { createFavoritePoster(context, posterItems) }
                        .getOrNull()
                        ?: createFallbackPoster(context)
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: return@runCatching false
                    val compressed = output.use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                    compressed
                }.getOrDefault(false)
            }
            exporting = false
            Toast.makeText(
                context,
                if (success) R.string.favorite_poster_saved else R.string.favorite_poster_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    HanimeScaffold(
        title = stringResource(R.string.favorite_poster_wall),
        onBack = onBack,
        actions = {
            FilledTonalIconButton(
                onClick = { shuffleSeed++ },
                enabled = posterItems.size > 1,
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_shuffle_24),
                    contentDescription = stringResource(R.string.favorite_poster_shuffle),
                )
            }
            FilledIconButton(
                onClick = {
                    val filename = "Han1meViewer_Favorites_" +
                        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
                    exportLauncher.launch(filename)
                },
                enabled = posterItems.isNotEmpty() && !exporting,
            ) {
                if (exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.baseline_share_24),
                        contentDescription = stringResource(R.string.favorite_poster_export),
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorite_poster_empty),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.favorite_poster_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    FavoritePosterPreview(items = posterItems)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun FavoritePosterPreview(
    items: List<FavoriteEntity>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF121114),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
        ) {
            Text(
                text = "Han1meViewer",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFE7A4A8),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.favorite_poster_wall),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = pluralStringResource(R.plurals.favorite_poster_count, items.size, items.size),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.62f),
            )
            Spacer(Modifier.height(10.dp))
            repeat(PosterRows) { rowIndex ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(PosterColumns) { columnIndex ->
                        val index = rowIndex * PosterColumns + columnIndex
                        val item = items.getOrNull(index)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                        ) {
                            if (item != null) {
                                PosterCoverFallback(
                                    title = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                AsyncImage(
                                    model = item.resolvedCoverUrl(),
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
                if (rowIndex < PosterRows - 1) Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.favorite_poster_footer),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

private fun Bitmap.asSoftwareBitmap(): Bitmap =
    if (config == Bitmap.Config.HARDWARE) {
        copy(Bitmap.Config.ARGB_8888, false)
    } else {
        this
    }

private fun createFallbackPoster(context: Context): Bitmap {
    val width = 1080
    val height = 1620
    val poster = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(poster)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        intArrayOf(0xFF0E0D10.toInt(), 0xFF211116.toInt(), 0xFF09090B.toInt()),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 68f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(context.getString(R.string.favorite_poster_wall), 72f, 180f, paint)
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 30f
    paint.color = 0xAAFFFFFF.toInt()
    canvas.drawText(context.getString(R.string.favorite_poster_footer), 72f, height - 72f, paint)
    return poster
}

private suspend fun createFavoritePoster(
    context: Context,
    items: List<FavoriteEntity>,
): Bitmap? {
    val width = 1080
    val height = 1620
    val poster = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(poster)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.shader = LinearGradient(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        intArrayOf(0xFF0E0D10.toInt(), 0xFF211116.toInt(), 0xFF09090B.toInt()),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    paint.shader = RadialGradient(
        width * 0.85f,
        height * 0.08f,
        width * 0.9f,
        intArrayOf(0x55FF6B7A, 0x00000000),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height * 0.45f, paint)
    paint.shader = null

    val margin = 72f
    val headerHeight = 286f
    val footerHeight = 92f
    val gap = 18f
    val cellWidth = (width - margin * 2 - gap * (PosterColumns - 1)) / PosterColumns
    val cellHeight = (height - margin - headerHeight - footerHeight - gap * (PosterRows - 1)) / PosterRows
    val top = margin + headerHeight

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = 0xFFE7A4A8.toInt()
    paint.textSize = 30f
    canvas.drawText("Han1meViewer", margin, margin + 28f, paint)

    paint.color = android.graphics.Color.WHITE
    paint.textSize = 68f
    canvas.drawText(context.getString(R.string.favorite_poster_wall), margin, margin + 112f, paint)

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.color = 0xAAFFFFFF.toInt()
    paint.textSize = 30f
    canvas.drawText(
        context.resources.getQuantityString(R.plurals.favorite_poster_count, items.size, items.size),
        margin,
        margin + 162f,
        paint,
    )

    val loader = SingletonImageLoader.get(context)
    val coverBitmaps = coroutineScope {
        List(PosterItemCount) { index ->
            async<Bitmap?>(Dispatchers.IO) {
                val item = items.getOrNull(index) ?: return@async null
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(item.resolvedCoverUrl())
                        .size(cellWidth.toInt(), cellHeight.toInt())
                        .build()
                    (loader.execute(request) as? SuccessResult)?.image?.toBitmap()?.asSoftwareBitmap()
                }.getOrNull()
            }
        }.awaitAll()
    }
    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    for (row in 0 until PosterRows) {
        for (column in 0 until PosterColumns) {
            val index = row * PosterColumns + column
            val left = margin + column * (cellWidth + gap)
            val cellTop = top + row * (cellHeight + gap)
            val rect = RectF(left, cellTop, left + cellWidth, cellTop + cellHeight)
            val item = items.getOrNull(index)
            val bitmap = coverBitmaps.getOrNull(index)

            if (bitmap != null) {
                runCatching {
                    imagePaint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                        .apply {
                            val scale = maxOf(
                                cellWidth / bitmap.width.toFloat(),
                                cellHeight / bitmap.height.toFloat(),
                            )
                            val dx = left + (cellWidth - bitmap.width * scale) / 2f
                            val dy = cellTop + (cellHeight - bitmap.height * scale) / 2f
                            setLocalMatrix(Matrix().apply {
                                setScale(scale, scale)
                                postTranslate(dx, dy)
                            })
                        }
                    canvas.drawRoundRect(rect, 18f, 18f, imagePaint)
                }
                imagePaint.shader = null
            } else {
                imagePaint.color = 0x22FFFFFF
                canvas.drawRoundRect(rect, 18f, 18f, imagePaint)
                item?.title?.take(14)?.let { title ->
                    paint.color = 0xCCFFFFFF.toInt()
                    paint.textSize = 26f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText(title, left + 14f, cellTop + cellHeight - 22f, paint)
                }
            }
        }
    }

    paint.color = 0x66FFFFFF
    paint.textSize = 26f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    canvas.drawText(
        context.getString(R.string.favorite_poster_footer),
        margin,
        height - 42f,
        paint,
    )
    return poster
}