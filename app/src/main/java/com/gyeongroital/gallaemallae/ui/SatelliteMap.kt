package com.gyeongroital.gallaemallae.ui

import com.gyeongroital.gallaemallae.domain.LatLng
import com.gyeongroital.gallaemallae.domain.MapCamera
import com.gyeongroital.gallaemallae.domain.Tile
import com.gyeongroital.gallaemallae.domain.WebMercator
import com.gyeongroital.gallaemallae.ui.theme.Tokens
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 위성 지도 한 장 + 그 위에 그리는 그림. 홈(반경 미리보기)과 추첨 화면이 함께 쓴다.
 * - [camera]는 그릴 때마다 불리므로 애니메이션 값을 읽어도 다시 그리기만 일어난다(재구성 없음).
 * - [preload]가 돌려주는 카메라들의 타일을 미리 받는다. 여러 줌의 타일을 낮은 줌부터 겹쳐 그려 줌 이동 중에도 빈칸이 없다.
 * - 키가 없거나 타일을 못 받으면 어두운 배경만 깔고 [overlay]는 그대로 그린다.
 */
@Composable
fun SatelliteMap(
    modifier: Modifier = Modifier,
    preloadKey: Any?,
    preload: (IntSize) -> List<MapCamera>,
    camera: (IntSize) -> MapCamera?,
    dim: Float = 0.3f,
    attributionPadding: Dp = Tokens.iviSpacingSmall,
    overlay: DrawScope.(cam: MapCamera, px: (LatLng) -> Offset) -> Unit = { _, _ -> },
    content: @Composable BoxScope.() -> Unit = {},
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val tiles = remember { mutableStateMapOf<Tile, ImageBitmap>() }

    LaunchedEffect(size, preloadKey) {
        if (size == IntSize.Zero || !SatelliteTiles.enabled) return@LaunchedEffect
        preload(size)
            .flatMap { WebMercator.visibleTiles(it, size.width, size.height, SatelliteTiles.MIN_ZOOM, SatelliteTiles.MAX_ZOOM) }
            .distinct()
            .filter { it !in tiles }
            .forEach { t ->
                SatelliteTiles.cached(t)?.let { tiles[t] = it.asImageBitmap() } ?: launch {
                    SatelliteTiles.request(t).await()?.let { tiles[t] = it.asImageBitmap() }
                }
            }
    }

    Box(modifier.onSizeChanged { size = it }) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Tokens.iviColorBackground)
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (w == 0 || h == 0) return@Canvas
            val cam = camera(IntSize(w, h)) ?: return@Canvas
            var drewTile = false
            tiles.entries.sortedBy { it.key.z }.forEach { (t, img) ->
                val (left, top, side) = WebMercator.tileRect(t, cam, w, h)
                if (left > w || top > h || left + side < 0 || top + side < 0) return@forEach
                if (side < 24 || side > 4096) return@forEach // 너무 작거나 크게 늘어난 타일은 건너뜀
                drawImage(
                    img,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(side.roundToInt() + 1, side.roundToInt() + 1), // 이음새 방지 1px 겹침
                )
                drewTile = true
            }
            if (drewTile) drawRect(Color.Black.copy(alpha = dim))
            overlay(cam) { p -> WebMercator.toScreen(p, cam, w, h).let { (x, y) -> Offset(x.toFloat(), y.toFloat()) } }
        }
        content()
        if (tiles.isNotEmpty()) {
            Text(
                SatelliteTiles.ATTRIBUTION,
                style = MaterialTheme.typography.bodyMedium,
                color = Tokens.iviColorOnSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).padding(attributionPadding),
            )
        }
    }
}

/**
 * 홈 화면 반경 미리보기. 고른 반경의 원이 지도에 그려지고, 반경을 바꾸면 원과 지도가 부드럽게 커지고 줄어든다.
 * 세 반경의 타일을 모두 미리 받아 두어 바꿀 때 빈칸이 없다.
 */
@Composable
fun RadiusPreviewMap(here: LatLng, radiusKm: Int, radiusOptions: List<Int>, modifier: Modifier = Modifier) {
    val animKm by androidx.compose.animation.core.animateFloatAsState(
        radiusKm.toFloat(),
        androidx.compose.animation.core.tween(Tokens.iviMotionRadiusChangeMs, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "radius",
    )
    val hereKey = (here.lat * 100).roundToInt() to (here.lng * 100).roundToInt()
    fun cam(sz: IntSize, km: Float) = MapCamera(here, WebMercator.fitZoom(here.lat, km * 1000.0, sz.width, sz.height, margin = 1.15))
    val primary = Tokens.iviColorPrimary
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Tokens.iviCardRadius),
        modifier = modifier,
    ) {
        SatelliteMap(
            modifier = Modifier.fillMaxSize(),
            preloadKey = hereKey,
            preload = { sz -> radiusOptions.map { cam(sz, it.toFloat()) } },
            camera = { sz -> cam(sz, animKm) },
            dim = 0.2f,
            overlay = { cam, px ->
                val center = px(here)
                val r = (animKm * 1000.0 / WebMercator.metersPerPx(here.lat, cam.zoom)).toFloat()
                drawCircle(primary.copy(alpha = 0.14f), r, center)
                drawCircle(
                    primary, r, center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        3.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 12.dp.toPx())),
                    ),
                )
                drawCircle(Color.White, 13.dp.toPx(), center)
                drawCircle(primary, 9.dp.toPx(), center)
            },
        ) {
            Text(
                "반경 ${radiusKm}km",
                style = MaterialTheme.typography.titleLarge,
                color = Tokens.iviColorOnSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Tokens.iviSpacingMedium)
                    .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(Tokens.iviButtonRadius))
                    .padding(horizontal = Tokens.iviSpacingSmall, vertical = 4.dp),
            )
        }
    }
}
