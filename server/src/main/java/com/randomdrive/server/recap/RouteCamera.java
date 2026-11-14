package com.randomdrive.server.recap;

import java.util.ArrayList;
import java.util.List;

/**
 * 경로 장면의 카메라 움직임(내비 느낌). 프레임마다 카메라와 경로 진행률을 미리 계산해 둔다.
 *
 * 1. 전체 보기(위에서 수직) → 2. 출발 쪽으로 내려오며 기울어짐 → 3. 차를 따라가며 진행 방향으로 회전
 * → 4. 다시 올라가 전체 보기.
 * 경로가 길수록 높이 떠서 따라간다(초당 화면 1~2개 분량만 지나가게).
 */
public final class RouteCamera {

    static final double PITCH = Math.toRadians(55);
    /** 경로 장면 안 시간표(초). */
    static final double HOLD_IN = 0.5, DESCEND = 0.9, FOLLOW = 3.5, RISE = 0.8;
    private static final double METERS_PER_WORLD0 = 156_543.03392;

    /** 줌 0 월드 좌표로 바꾼 경로 조각들과 누적 길이. */
    final List<double[][]> polylines = new ArrayList<>();
    final List<double[]> cumulative = new ArrayList<>();
    final double totalLength;
    final Camera overview;
    private final Camera[] cameras;
    private final double[] progress;

    private RouteCamera(List<List<double[]>> routeLatLng, List<double[]> extraLatLng, int width, int height, int frames,
                        double fps) {
        double run = 0;
        for (List<double[]> seg : routeLatLng) {
            double[][] pts = new double[seg.size()][];
            double[] cum = new double[seg.size()];
            for (int i = 0; i < seg.size(); i++) {
                pts[i] = world(seg.get(i));
                if (i > 0) {
                    run += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
                }
                cum[i] = run;
            }
            polylines.add(pts);
            cumulative.add(cum);
        }
        totalLength = run;

        List<double[]> all = new ArrayList<>();
        polylines.forEach(p -> all.addAll(List.of(p)));
        extraLatLng.forEach(ll -> all.add(world(ll)));
        overview = fitOverview(all, width, height, 150);

        cameras = new Camera[frames];
        progress = new double[frames];
        if (totalLength <= 0) {
            for (int i = 0; i < frames; i++) {
                cameras[i] = overview;
                progress[i] = 1;
            }
            return;
        }
        double lat = latOf(overview.centerY());
        double totalKm = totalLength * METERS_PER_WORLD0 * Math.cos(Math.toRadians(lat)) / 1000;
        double viewKm = Math.max(3, Math.min(30, totalKm / 5));
        double followZoom = log2(METERS_PER_WORLD0 * Math.cos(Math.toRadians(lat)) * width / (viewKm * 1000));
        followZoom = Math.min(16, Math.max(followZoom, overview.zoom() + 0.4));
        double lookAhead = viewKm * 1000 * 0.35 / (METERS_PER_WORLD0 * Math.cos(Math.toRadians(lat)));

        // 진행률과 따라가는 카메라(방향은 앞쪽을 보고, 흔들리지 않게 부드럽게)
        double[] heading = new double[frames];
        double[][] head = new double[frames][];
        for (int i = 0; i < frames; i++) {
            double t = i / fps;
            progress[i] = RecapPainter.easeInOut(RecapPainter.clamp((t - HOLD_IN - DESCEND) / FOLLOW));
            double s = progress[i] * totalLength;
            head[i] = pointAt(s);
            // 뒤로도 멀리 본다: 도착 후 목적지 주변을 맴돈 기록이 있어도 끝에서 카메라가 휘청이지 않게
            double[] a = pointAt(Math.max(0, s - lookAhead * 0.8));
            double[] b = pointAt(Math.min(totalLength, s + lookAhead));
            heading[i] = Math.atan2(b[0] - a[0], -(b[1] - a[1]));
        }
        smoothAngles(heading, 0.12);

        for (int i = 0; i < frames; i++) {
            double t = i / fps;
            Camera follow = new Camera(head[i][0], head[i][1], followZoom, heading[i], PITCH);
            if (t < HOLD_IN) {
                cameras[i] = overview;
            } else if (t < HOLD_IN + DESCEND) {
                cameras[i] = Camera.lerp(overview, follow, RecapPainter.easeInOut((t - HOLD_IN) / DESCEND));
            } else if (t < HOLD_IN + DESCEND + FOLLOW) {
                cameras[i] = follow;
            } else {
                double f = RecapPainter.clamp((t - HOLD_IN - DESCEND - FOLLOW) / RISE);
                cameras[i] = Camera.lerp(follow, overview, RecapPainter.easeInOut(f));
            }
        }
    }

    public static RouteCamera plan(RecapScene scene, int width, int height, int frames, double fps) {
        List<double[]> extra = new ArrayList<>(scene.stops());
        scene.waypoints().forEach(w -> extra.add(w.latLng()));
        extra.add(scene.destination());
        return new RouteCamera(scene.route(), extra, width, height, frames, fps);
    }

    public int frames() {
        return cameras.length;
    }

    public Camera camera(int frame) {
        return cameras[frame];
    }

    public double progress(int frame) {
        return progress[frame];
    }

    /** 경로 처음부터 길이 s 지점(줌 0 월드 좌표). */
    double[] pointAt(double s) {
        double[] last = null;
        for (int k = 0; k < polylines.size(); k++) {
            double[][] pts = polylines.get(k);
            double[] cum = cumulative.get(k);
            for (int i = 1; i < pts.length; i++) {
                if (cum[i] >= s) {
                    double seg = cum[i] - cum[i - 1];
                    double f = seg <= 0 ? 0 : (s - cum[i - 1]) / seg;
                    f = RecapPainter.clamp(f);
                    return new double[]{pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * f,
                            pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * f};
                }
            }
            last = pts[pts.length - 1];
        }
        return last == null ? new double[]{overview.centerX(), overview.centerY()} : last;
    }

    /** 경로에서 p와 가장 가까운 점까지의 누적 길이. */
    double nearestAlong(double[] p) {
        double best = Double.MAX_VALUE, along = 0;
        for (int k = 0; k < polylines.size(); k++) {
            double[][] pts = polylines.get(k);
            for (int i = 0; i < pts.length; i++) {
                double d = Math.hypot(pts[i][0] - p[0], pts[i][1] - p[1]);
                if (d < best) {
                    best = d;
                    along = cumulative.get(k)[i];
                }
            }
        }
        return along;
    }

    static double[] world(double[] latLng) {
        return new double[]{MapView.worldX(latLng[1], 0), MapView.worldY(latLng[0], 0)};
    }

    /** 위에서 내려다본 전체 보기. 경로 상자의 가운데가 화면 가운데에 오게(카메라 타깃은 화면 아래쪽이라 보정). */
    private static Camera fitOverview(List<double[]> pts, int width, int height, int padding) {
        if (pts.isEmpty()) {
            return new Camera(MapView.worldX(127.8, 0), MapView.worldY(36.5, 0), 7, 0, 0);
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] p : pts) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        double spanX = Math.max(maxX - minX, 1e-9), spanY = Math.max(maxY - minY, 1e-9);
        double zoom = Math.min(log2((width - 2.0 * padding) / spanX), log2((height - 2.0 * padding) / spanY));
        zoom = Math.max(5, Math.min(15, zoom));
        double scale = Math.pow(2, zoom);
        double offset = (height * PerspectiveMap.TARGET_Y - height / 2.0) / scale;
        return new Camera((minX + maxX) / 2, (minY + maxY) / 2 + offset, zoom, 0, 0);
    }

    /** 각도 배열을 앞뒤로 지수 평활(±π 경계를 넘지 않게 풀어서). */
    private static void smoothAngles(double[] a, double alpha) {
        for (int i = 1; i < a.length; i++) {
            a[i] = a[i - 1] + Math.atan2(Math.sin(a[i] - a[i - 1]), Math.cos(a[i] - a[i - 1]));
        }
        for (int i = 1; i < a.length; i++) {
            a[i] = a[i - 1] + (a[i] - a[i - 1]) * alpha;
        }
        for (int i = a.length - 2; i >= 0; i--) {
            a[i] = a[i + 1] + (a[i] - a[i + 1]) * alpha;
        }
    }

    private static double latOf(double worldY0) {
        double n = Math.PI - 2 * Math.PI * worldY0 / MapView.TILE;
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    private static double log2(double v) {
        return Math.log(v) / Math.log(2);
    }
}
