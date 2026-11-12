package com.randomdrive.server.recap;

/**
 * 지도 카메라 한 순간.
 *
 * @param centerX 화면 기준점(타깃)의 월드 좌표, 줌 0 기준 픽셀(0~256)
 * @param centerY 〃
 * @param zoom    소수 줌(타깃 위치에서 지도 1px = 화면 1px이 되는 줌)
 * @param bearing 진행 방향, 라디안. 0 = 북쪽이 화면 위, 시계 방향
 * @param pitch   기울기, 라디안. 0 = 위에서 수직으로 내려다봄
 */
public record Camera(double centerX, double centerY, double zoom, double bearing, double pitch) {

    /** a→b 사이를 f(0~1)만큼. 방향은 짧은 쪽으로 돈다. */
    public static Camera lerp(Camera a, Camera b, double f) {
        double db = Math.atan2(Math.sin(b.bearing - a.bearing), Math.cos(b.bearing - a.bearing));
        return new Camera(
                a.centerX + (b.centerX - a.centerX) * f,
                a.centerY + (b.centerY - a.centerY) * f,
                a.zoom + (b.zoom - a.zoom) * f,
                a.bearing + db * f,
                a.pitch + (b.pitch - a.pitch) * f);
    }
}
