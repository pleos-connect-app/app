package ai.randomdrive.app.ui

import ai.randomdrive.app.domain.Geo
import ai.randomdrive.app.domain.LatLng
import ai.randomdrive.app.domain.MapCamera
import ai.randomdrive.app.domain.Tile
import ai.randomdrive.app.domain.WebMercator
import ai.randomdrive.app.ui.theme.Tokens
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ---------------- 추첨 중: 지도 위 핀 탐색 → 착지 ----------------
//
// 정차(P) 중에만 보이는 화면(DO 미선언). 서버 응답을 기다리는 동안의 로딩 표시를 겸한다(가이드: 2초 넘는 동작은 로딩 UI).
// - 반경 원 안에서 핀이 빠르게 옮겨 다니다가, 결과가 오면 점점 느려지며 실제 목적지에 착지한다(가짜 결과로 끝나지 않음).
// - 화면을 누르면 바로 결과로 넘어간다. 시스템 '애니메이션 끄기'면 연출 없이 바로 넘어간다.
// - 위성 지도 키가 없거나 타일을 못 받으면 어두운 배경 + 거리 원만 그린다.

private data class Hop(val at: LatLng, val bornMs: Long)

private const val TRAIL_FADE_MS = 600L
private const val POP_MS = 140L
private const val SWEEP_PERIOD_MS = 1_600L
private const val RIPPLE_PERIOD_MS = 1_100L
private const val MAX_TRAIL = 14

@Composable
fun DrawingScreen(d: PreTrip.Drawing, onLanded: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val target by rememberUpdatedState(d.pending?.destination)
    val landedCallback by rememberUpdatedState(onLanded)

    val hops = remember { mutableStateListOf<Hop>() }
    var landed by remember { mutableStateOf<Hop?>(null) }
    var skip by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val tiles = remember { mutableStateMapOf<Tile, ImageBitmap>() }

    // 프레임 시계: 이 화면이 떠 있는 2~3초 동안만 매 프레임 다시 그린다(화면을 벗어나면 함께 멈춤).
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { now = SystemClock.uptimeMillis() }
    }

    val cam = remember(size, d.origin, d.radiusKm) {
        if (size == IntSize.Zero) null
        else MapCamera(d.origin, WebMercator.fitZoom(d.origin.lat, d.radiusKm * 1000.0, size.width, size.height))
    }

    // 위성 타일(홈 화면에서 미리 받기 시작한 요청을 이어받음)
    LaunchedEffect(cam) {
        val c = cam ?: return@LaunchedEffect
        if (!SatelliteTiles.enabled) return@LaunchedEffect
        WebMercator.visibleTiles(c, size.width, size.height, SatelliteTiles.MIN_ZOOM, SatelliteTiles.MAX_ZOOM).forEach { t ->
            SatelliteTiles.cached(t)?.let { tiles[t] = it.asImageBitmap() } ?: launch {
                SatelliteTiles.request(t).await()?.let { tiles[t] = it.asImageBitmap() }
            }
        }
    }

    // 핀 연출
    LaunchedEffect(d.origin, d.radiusKm) {
        if (reduceMotion) {
            snapshotFlow { target }.first { it != null }
            landedCallback()
            return@LaunchedEffect
        }
        val rnd = Random(System.nanoTime())
        val radiusM = d.radiusKm * 1000.0
        // 원 안에 고르게 퍼지도록 sqrt 분포, 너무 가운데·가장자리는 피함
        fun randomPoint() = Geo.destination(d.origin, radiusM * sqrt(rnd.nextDouble(0.03, 0.85)), rnd.nextDouble(0.0, 360.0))
        fun hop(p: LatLng) {
            hops += Hop(p, SystemClock.uptimeMillis())
            if (hops.size > MAX_TRAIL) hops.removeAt(0)
        }
        // skip이 눌리면 남은 대기를 끊는다
        suspend fun pause(ms: Long) {
            withTimeoutOrNull(ms) { snapshotFlow { skip }.first { it } }
        }

        val start = SystemClock.uptimeMillis()
        while (target == null || (!skip && SystemClock.uptimeMillis() - start < Tokens.iviMotionDrawMinSpinMs)) {
            hop(randomPoint())
            delay(Tokens.iviMotionDrawHopMs)
        }
        val dest = target!!.latLng
        val landing = Tokens.iviMotionDrawLandingHopsMs
        for ((i, ms) in landing.withIndex()) {
            if (skip) break
            // 점점 목적지 쪽으로 좁혀 들어간다
            val k = (i + 1).toDouble() / (landing.size + 1)
            val r = randomPoint()
            hop(LatLng(r.lat + (dest.lat - r.lat) * k, r.lng + (dest.lng - r.lng) * k))
            pause(ms)
        }
        landed = Hop(dest, SystemClock.uptimeMillis())
        if (!skip) pause(Tokens.iviMotionDrawHoldMs)
        landedCallback()
    }

    val primary = Tokens.iviColorPrimary
    val highlight = Tokens.iviColorHighlight
    val textColor = Tokens.iviColorOnSurface // 지도·어두운 배경 위라 테마와 관계없이 밝은 글자
    val mutedColor = Tokens.iviColorOnSurfaceVariant

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .semantics { onClick(label = "바로 결과 보기") { skip = true; true } }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skip = true },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Tokens.iviColorBackground)
            val c = cam ?: return@Canvas
            val w = size.width.toInt()
            val h = size.height.toInt()
            fun px(p: LatLng): Offset = WebMercator.toScreen(p, c, w, h).let { (x, y) -> Offset(x.toFloat(), y.toFloat()) }

            // 1) 위성 타일 + 어둡게(글자·핀 대비)
            tiles.forEach { (t, img) ->
                val (left, top, side) = WebMercator.tileRect(t, c, w, h)
                drawImage(
                    img,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(side.roundToInt() + 1, side.roundToInt() + 1), // 이음새 방지 1px 겹침
                )
            }
            if (tiles.isNotEmpty()) drawRect(Color.Black.copy(alpha = 0.35f))

            val center = px(d.origin)
            val radiusPx = (d.radiusKm * 1000.0 / WebMercator.metersPerPx(d.origin.lat, c.zoom)).toFloat()

            // 2) 지도가 없을 때: 거리 원(1/3, 2/3)
            if (tiles.isEmpty()) {
                listOf(1f / 3, 2f / 3).forEach { f ->
                    drawCircle(mutedColor.copy(alpha = 0.18f), radiusPx * f, center, style = Stroke(1.dp.toPx()))
                }
            }

            // 3) 반경 원
            drawCircle(primary.copy(alpha = 0.10f), radiusPx, center)
            drawCircle(
                primary.copy(alpha = 0.8f), radiusPx, center,
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 12.dp.toPx()))),
            )

            val land = landed
            // 4) 레이더 스윕(탐색 중에만)
            if (land == null) {
                val angle = (now % SWEEP_PERIOD_MS) / SWEEP_PERIOD_MS.toFloat() * 360f
                rotate(angle, center) {
                    drawCircle(
                        Brush.sweepGradient(0.75f to Color.Transparent, 1f to primary.copy(alpha = 0.35f), center = center),
                        radiusPx, center,
                    )
                }
            }

            // 5) 출발 위치
            drawCircle(Color.White, 13.dp.toPx(), center)
            drawCircle(primary, 9.dp.toPx(), center)

            // 6) 지나간 핀 자국
            val trail = if (land == null) hops.dropLast(1) else hops
            trail.forEach { hp ->
                val a = 1f - ((now - hp.bornMs).toFloat() / TRAIL_FADE_MS).coerceIn(0f, 1f)
                if (a > 0f) drawCircle(primary.copy(alpha = 0.7f * a), 9.dp.toPx(), px(hp.at))
            }

            if (land == null) {
                // 7) 지금 핀: 출발지에서 점선 + 톡 튀어나오는 핀
                hops.lastOrNull()?.let { cur ->
                    val at = px(cur.at)
                    drawLine(
                        Color.White.copy(alpha = 0.5f), center, at, 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx())),
                    )
                    val pop = ((now - cur.bornMs).toFloat() / POP_MS).coerceIn(0f, 1f)
                    drawPin(at, primary, 0.6f + 0.4f * pop)
                }
            } else {
                // 8) 착지: 실선 경로 + 퍼지는 물결 + 큰 핀
                val at = px(land.at)
                drawLine(highlight.copy(alpha = 0.9f), center, at, 3.dp.toPx())
                val age = now - land.bornMs
                repeat(2) { i ->
                    val t = (((age + i * RIPPLE_PERIOD_MS / 2) % RIPPLE_PERIOD_MS).toFloat() / RIPPLE_PERIOD_MS)
                    drawCircle(highlight.copy(alpha = 0.6f * (1f - t)), 10.dp.toPx() + t * 70.dp.toPx(), at, style = Stroke(3.dp.toPx()))
                }
                val drop = (age.toFloat() / 220f).coerceIn(0f, 1f)
                drawPin(Offset(at.x, at.y - (1f - drop) * 40.dp.toPx()), highlight, 1.25f)
            }

            // 9) 위·아래 그라데이션(글자 대비)
            drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.3f to Color.Transparent))
            drawRect(Brush.verticalGradient(0.8f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)))
        }

        // 글자
        Column(
            Modifier.fillMaxWidth().padding(Tokens.iviSpacingLarge),
            verticalArrangement = Arrangement.spacedBy(Tokens.iviSpacingSmall),
        ) {
            val land = landed
            val dest = d.pending?.destination
            if (land == null || dest == null) {
                Text("반경 ${d.radiusKm}km 안에서 뽑는 중…", style = MaterialTheme.typography.titleLarge, color = textColor)
            } else {
                val a = ((now - land.bornMs) / 250f).coerceIn(0f, 1f)
                Column(Modifier.alpha(a), verticalArrangement = Arrangement.spacedBy(Tokens.iviSpacingSmall)) {
                    Text("오늘의 목적지", style = MaterialTheme.typography.bodyLarge, color = mutedColor)
                    Text(dest.name, style = MaterialTheme.typography.displaySmall, color = highlight)
                    val km = Geo.distanceMeters(d.origin, dest.latLng) / 1000.0
                    Text(String.format(Locale.KOREA, "직선 %.0fkm", km), style = MaterialTheme.typography.bodyLarge, color = textColor)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(Tokens.iviSpacingLarge),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("화면을 누르면 바로 결과를 볼 수 있어요", style = MaterialTheme.typography.bodyMedium, color = mutedColor, modifier = Modifier.weight(1f))
            if (tiles.isNotEmpty()) Text(SatelliteTiles.ATTRIBUTION, style = MaterialTheme.typography.bodyMedium, color = mutedColor)
        }
    }
}

/** 물방울 모양 핀. [tip]이 바닥 끝(지점). */
private fun DrawScope.drawPin(tip: Offset, color: Color, scale: Float) {
    val r = 22.dp.toPx() * scale
    val head = Offset(tip.x, tip.y - r * 2.2f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(head.x - r * 0.75f, head.y + r * 0.6f)
        lineTo(head.x + r * 0.75f, head.y + r * 0.6f)
        close()
    }
    drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.5f, Offset(tip.x, tip.y + 2.dp.toPx())) // 그림자
    drawPath(path, color)
    drawCircle(color, r, head)
    drawCircle(Color.White, r * 0.4f, head)
}
