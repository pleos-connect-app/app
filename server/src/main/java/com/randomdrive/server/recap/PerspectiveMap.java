package com.randomdrive.server.recap;

import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;

/**
 * 기울인 지도(2.5D) 렌더러. 내비처럼 카메라가 바닥을 비스듬히 내려다본다.
 *
 * 좌표계: 바닥 X=오른쪽, Y=진행 방향(앞), Z=위. 카메라는 타깃(화면의 TARGET_Y 지점)에서 거리 D만큼 떨어져
 * pitch만큼 기울어져 있다. D = 초점거리 f라서 타깃 위치에서는 지도 1px(현재 줌) = 화면 1px.
 * 화면 한 줄(행)은 바닥의 한 직선에 대응하고 그 줄 안에서는 배율이 일정하므로, 행마다 시작점과 증분만 구해
 * 픽셀을 채운다. 먼 행일수록 배율이 커지므로 행마다 더 낮은 줌의 타일을 쓴다(밉맵).
 */
public final class PerspectiveMap {

    /** 세로 시야각. */
    private static final double FOV = Math.toRadians(40);
    /** 타깃(차 위치)을 화면 아래쪽에 둬서 앞이 더 많이 보이게. */
    static final double TARGET_Y = 0.64;
    private static final int MIN_TILE_ZOOM = 5;
    private static final int MAX_TILE_ZOOM = 17;
    /** 0이면 반올림. 클수록 낮은 줌 타일을 써서 요청 수가 줄고 조금 흐려진다. */
    private static final double MIP_BIAS = 0.2;

    private static final int GROUND = 0x15202b;
    private static final int GRID = 0x24384a;

    private final int width;
    private final int height;
    private final double f;
    private final double cx;
    private final double cy;
    private final TileCache tiles;

    PerspectiveMap(int width, int height, TileCache tiles) {
        this.width = width;
        this.height = height;
        this.f = height / 2.0 / Math.tan(FOV / 2);
        this.cx = width / 2.0;
        this.cy = height * TARGET_Y;
        this.tiles = tiles;
    }

    /** 바닥을 out(width×height, RGB)에 그린다. */
    void renderGround(Camera cam, int[] out) {
        double cos = Math.cos(cam.bearing()), sin = Math.sin(cam.bearing());
        double scale = Math.pow(2, cam.zoom());
        double centerWx = cam.centerX() * scale, centerWy = cam.centerY() * scale;
        for (int row = 0; row < height; row++) {
            int base = row * width;
            Ray ray = ray(cam.pitch(), row + 0.5 - cy);
            if (ray == null) {
                for (int i = 0; i < width; i++) {
                    out[base + i] = GROUND;
                }
                continue;
            }
            int tz = tileZoom(cam.zoom(), ray.t);
            double toTz = Math.pow(2, tz - cam.zoom());
            // 행의 왼쪽 끝(u = -cx + 0.5)과 한 픽셀당 증분, 타일 줌 픽셀 단위
            double u0 = 0.5 - cx;
            double x0 = ray.t * u0, y0 = ray.y;
            double wx = (centerWx + x0 * cos + y0 * sin) * toTz;
            double wy = (centerWy + x0 * sin - y0 * cos) * toTz;
            double dx = ray.t * cos * toTz, dy = ray.t * sin * toTz;
            int worldSize = MapView.TILE << tz;

            int lastTx = Integer.MIN_VALUE, lastTy = Integer.MIN_VALUE;
            int[] tile = null;
            for (int i = 0; i < width; i++, wx += dx, wy += dy) {
                int px = (int) Math.floor(wx), py = (int) Math.floor(wy);
                if (py < 0 || py >= worldSize) {
                    out[base + i] = GROUND;
                    continue;
                }
                px = Math.floorMod(px, worldSize);
                int tx = px >> 8, ty = py >> 8;
                if (tx != lastTx || ty != lastTy) {
                    tile = tiles == null ? null : tiles.get(tz, tx, ty);
                    lastTx = tx;
                    lastTy = ty;
                }
                int lx = px & 255, ly = py & 255;
                out[base + i] = tile != null ? tile[(ly << 8) | lx]
                        : (lx < 2 || ly < 2) ? GRID : GROUND;
            }
        }
    }

    /** 이 카메라로 그릴 때 필요한 타일(성기게 표본을 떠서). */
    void collectTiles(Camera cam, Set<Long> into) {
        double cos = Math.cos(cam.bearing()), sin = Math.sin(cam.bearing());
        double scale = Math.pow(2, cam.zoom());
        double centerWx = cam.centerX() * scale, centerWy = cam.centerY() * scale;
        for (int row = 0; row < height; row += 16) {
            Ray ray = ray(cam.pitch(), row + 0.5 - cy);
            if (ray == null) {
                continue;
            }
            int tz = tileZoom(cam.zoom(), ray.t);
            double toTz = Math.pow(2, tz - cam.zoom());
            int worldSize = MapView.TILE << tz;
            for (int col = 0; col <= width; col += 32) {
                double x = ray.t * (col - cx);
                double wx = (centerWx + x * cos + ray.y * sin) * toTz;
                double wy = (centerWy + x * sin - ray.y * cos) * toTz;
                if (wy < 0 || wy >= worldSize) {
                    continue;
                }
                int px = Math.floorMod((int) Math.floor(wx), worldSize);
                into.add(TileCache.key(tz, px >> 8, ((int) Math.floor(wy)) >> 8));
            }
        }
    }

    /**
     * 월드 좌표(줌 0 픽셀) → 화면 좌표. 카메라 뒤나 지평선 너머면 null.
     * 반환값의 z에는 그 지점의 원근 배율(가까우면 1보다 큼)을 담는다.
     */
    Projected project(Camera cam, double worldX0, double worldY0) {
        double scale = Math.pow(2, cam.zoom());
        double dx = (worldX0 - cam.centerX()) * scale, dy = (worldY0 - cam.centerY()) * scale;
        double cos = Math.cos(cam.bearing()), sin = Math.sin(cam.bearing());
        double x = dx * cos + dy * sin;
        double y = dx * sin - dy * cos;
        double sp = Math.sin(cam.pitch()), cp = Math.cos(cam.pitch());
        double depth = y * sp + f;          // 카메라 앞쪽 거리(D = f)
        if (depth < f * 0.05) {
            return null;
        }
        double k = f / depth;
        return new Projected(cx + x * k, cy - y * cp * k, k);
    }

    record Projected(double x, double y, double scale) {
        Point2D.Double point() {
            return new Point2D.Double(x, y);
        }
    }

    /** 화면 행 v(타깃 기준, 아래가 +)가 바닥과 만나는 곳: 행 배율 t, 앞쪽 거리 y. 지평선 위면 null. */
    private Ray ray(double pitch, double v) {
        double sp = Math.sin(pitch), cp = Math.cos(pitch);
        double denom = f * cp + v * sp;
        if (denom <= f * 0.02) {
            return null;
        }
        double t = f * cp / denom;                 // D = f
        double y = -f * sp + t * (f * sp - v * cp);
        return new Ray(t, y);
    }

    private record Ray(double t, double y) {
    }

    /** 화면 1px이 타일 1px쯤 되는 줌(멀수록 낮게). */
    private static int tileZoom(double zoom, double rowScale) {
        int tz = (int) Math.floor(zoom - Math.log(rowScale) / Math.log(2) + 0.5 - MIP_BIAS);
        return Math.max(MIN_TILE_ZOOM, Math.min(MAX_TILE_ZOOM, tz));
    }
}
