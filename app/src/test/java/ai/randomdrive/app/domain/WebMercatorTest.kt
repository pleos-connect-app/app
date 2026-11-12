package ai.randomdrive.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebMercatorTest {
    private val seoul = LatLng(37.5547, 126.9707)

    @Test
    fun originProjectsToWorldCenter() {
        val (x, y) = WebMercator.project(LatLng(0.0, 0.0), 0.0)
        assertEquals(128.0, x, 1e-9)
        assertEquals(128.0, y, 1e-9)
    }

    @Test
    fun knownTileForSeoul() {
        // 서울역은 줌 10에서 x=873, y=396 타일 (표준 XYZ)
        val (x, y) = WebMercator.project(seoul, 10.0)
        assertEquals(873, (x / 256).toInt())
        assertEquals(396, (y / 256).toInt())
    }

    @Test
    fun fitZoomPutsRadiusInsideShortSide() {
        listOf(50, 100, 150).forEach { km ->
            val z = WebMercator.fitZoom(seoul.lat, km * 1000.0, 1682, 1440)
            val radiusPx = km * 1000.0 / WebMercator.metersPerPx(seoul.lat, z)
            assertEquals(1440 / 2.0 / 1.25, radiusPx, 1.0)
        }
    }

    @Test
    fun screenDistanceMatchesGroundDistance() {
        val cam = MapCamera(seoul, WebMercator.fitZoom(seoul.lat, 100_000.0, 840, 1440))
        val east = Geo.destination(seoul, 50_000.0, 90.0)
        val (sx, sy) = WebMercator.toScreen(seoul, cam, 840, 1440)
        val (ex, ey) = WebMercator.toScreen(east, cam, 840, 1440)
        assertEquals(420.0, sx, 1e-6)
        assertEquals(720.0, sy, 1e-6)
        val px = ex - sx
        assertEquals(50_000.0 / WebMercator.metersPerPx(seoul.lat, cam.zoom), px, px * 0.02)
        assertEquals(0.0, ey - sy, 2.0)
    }

    @Test
    fun visibleTilesCoverTheScreen() {
        val cam = MapCamera(seoul, 9.4)
        val tiles = WebMercator.visibleTiles(cam, 1682, 1440, 6, 18)
        assertTrue(tiles.all { it.z == 9 })
        val rects = tiles.map { WebMercator.tileRect(it, cam, 1682, 1440) }
        assertTrue(rects.minOf { it.first } <= 0.0)
        assertTrue(rects.minOf { it.second } <= 0.0)
        assertTrue(rects.maxOf { it.first + it.third } >= 1682.0)
        assertTrue(rects.maxOf { it.second + it.third } >= 1440.0)
        assertTrue("타일 수가 과하지 않아야 함: ${tiles.size}", tiles.size <= 60)
    }
}
