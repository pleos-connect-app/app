package ai.randomdrive.app.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.tan

/** 지도 타일 한 장(XYZ 방식, 256px). */
data class Tile(val z: Int, val x: Int, val y: Int)

/** 화면 한가운데에 [center]가 오고 [zoom](소수 가능) 배율로 보는 지도 카메라. */
data class MapCamera(val center: LatLng, val zoom: Double)

/**
 * 웹 메르카토르(EPSG:3857) 계산. 위성 타일(VWorld 등)과 같은 좌표계라
 * 지도 라이브러리 없이 타일 배치와 좌표→화면 변환을 직접 한다.
 */
object WebMercator {
    const val TILE_PX = 256
    /** 줌 0에서 적도 1px의 길이(m). */
    private const val EQUATOR_M_PER_PX = 156_543.033_92

    fun worldPx(zoom: Double): Double = TILE_PX * 2.0.pow(zoom)

    /** 해당 줌의 월드 픽셀 좌표 (x, y). */
    fun project(p: LatLng, zoom: Double): Pair<Double, Double> {
        val w = worldPx(zoom)
        val lat = Math.toRadians(p.lat.coerceIn(-85.0511, 85.0511))
        val x = (p.lng + 180.0) / 360.0 * w
        val y = (1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0 * w
        return x to y
    }

    fun metersPerPx(lat: Double, zoom: Double): Double = EQUATOR_M_PER_PX * cos(Math.toRadians(lat)) / 2.0.pow(zoom)

    /** 반경 [radiusM] 원이 w×h 화면의 짧은 변 안에 [margin]배 여유를 두고 들어가는 줌. */
    fun fitZoom(lat: Double, radiusM: Double, widthPx: Int, heightPx: Int, margin: Double = 1.25): Double {
        val halfPx = minOf(widthPx, heightPx) / 2.0
        return log2(EQUATOR_M_PER_PX * cos(Math.toRadians(lat)) * halfPx / (radiusM * margin))
    }

    /** 화면 좌표(px, 왼쪽 위 원점). */
    fun toScreen(p: LatLng, cam: MapCamera, widthPx: Int, heightPx: Int): Pair<Double, Double> {
        val (px, py) = project(p, cam.zoom)
        val (cx, cy) = project(cam.center, cam.zoom)
        return (px - cx + widthPx / 2.0) to (py - cy + heightPx / 2.0)
    }

    /** 타일은 정수 줌만 있으므로 내림한 줌의 타일을 확대해 그린다(확대 배율 1~2). */
    fun tileZoom(cam: MapCamera, minZoom: Int, maxZoom: Int): Int = floor(cam.zoom).toInt().coerceIn(minZoom, maxZoom)

    /** 화면을 덮는 타일 목록. */
    fun visibleTiles(cam: MapCamera, widthPx: Int, heightPx: Int, minZoom: Int, maxZoom: Int): List<Tile> {
        val z = tileZoom(cam, minZoom, maxZoom)
        val scale = 2.0.pow(cam.zoom - z)
        val (cx, cy) = project(cam.center, z.toDouble())
        val halfW = widthPx / 2.0 / scale
        val halfH = heightPx / 2.0 / scale
        val n = 1 shl z
        val x0 = floor((cx - halfW) / TILE_PX).toInt()
        val x1 = floor((cx + halfW) / TILE_PX).toInt()
        val y0 = floor((cy - halfH) / TILE_PX).toInt().coerceAtLeast(0)
        val y1 = floor((cy + halfH) / TILE_PX).toInt().coerceAtMost(n - 1)
        return buildList {
            for (y in y0..y1) for (x in x0..x1) add(Tile(z, Math.floorMod(x, n), y))
        }
    }

    /** 타일의 화면상 왼쪽 위 좌표와 한 변 길이(px). */
    fun tileRect(t: Tile, cam: MapCamera, widthPx: Int, heightPx: Int): Triple<Double, Double, Double> {
        val scale = 2.0.pow(cam.zoom - t.z)
        val (cx, cy) = project(cam.center, t.z.toDouble())
        val left = (t.x * TILE_PX - cx) * scale + widthPx / 2.0
        val top = (t.y * TILE_PX - cy) * scale + heightPx / 2.0
        return Triple(left, top, TILE_PX * scale)
    }
}
