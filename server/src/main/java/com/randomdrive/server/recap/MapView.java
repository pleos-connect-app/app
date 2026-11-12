package com.randomdrive.server.recap;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 경로가 화면에 꽉 차게 들어가는 지도 한 장(Web Mercator, 256px 타일).
 * 줌은 소수까지 쓴다: 한 단계 더 큰 타일 줌을 받아 scale(0.5~1)배로 줄여 그리므로 경로가 화면을 채우고 타일도 선명하다.
 * 배경 이미지는 한 번만 만들고, 프레임마다 그 위에 경로를 그린다.
 */
public final class MapView {

    static final int TILE = 256;
    static final double MIN_ZOOM = 5;
    static final double MAX_ZOOM = 16;

    private final int width;
    private final int height;
    /** 타일을 받을 정수 줌. */
    private final int tileZoom;
    /** 타일 줌 대비 화면 배율(0.5 < scale ≤ 1). */
    private final double scale;
    /** 화면 왼쪽 위 모서리의 화면 배율 기준 월드 픽셀 좌표. */
    private final double originX;
    private final double originY;

    private MapView(int width, int height, double zoom, double centerWorldX0, double centerWorldY0) {
        this.width = width;
        this.height = height;
        this.tileZoom = (int) Math.ceil(zoom - 1e-9);
        this.scale = Math.pow(2, zoom - tileZoom);
        double px = Math.pow(2, zoom);
        this.originX = centerWorldX0 * px - width / 2.0;
        this.originY = centerWorldY0 * px - height / 2.0;
    }

    /** 좌표들이 padding을 남기고 width×height 안에 들어가는 가장 큰 줌. 좌표가 없으면 한국 중심. */
    public static MapView fit(List<double[]> latLngs, int width, int height, int padding) {
        if (latLngs.isEmpty()) {
            return new MapView(width, height, 7, worldX(127.8, 0), worldY(36.5, 0));
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] ll : latLngs) {
            double x = worldX(ll[1], 0), y = worldY(ll[0], 0);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        double spanX = Math.max(maxX - minX, 1e-9), spanY = Math.max(maxY - minY, 1e-9);
        double zx = log2((width - 2.0 * padding) / spanX), zy = log2((height - 2.0 * padding) / spanY);
        double zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(zx, zy)));
        return new MapView(width, height, zoom, (minX + maxX) / 2, (minY + maxY) / 2);
    }

    public int tileZoom() {
        return tileZoom;
    }

    public Point2D.Double project(double lat, double lng) {
        double px = Math.pow(2, tileZoom) * scale; // = 2^zoom
        return new Point2D.Double(worldX(lng, 0) * px - originX, worldY(lat, 0) * px - originY);
    }

    /**
     * 배경 이미지. tiles가 타일을 주면 위성 지도, null이면(키 없음·실패) 어두운 단색 그라데이션.
     * 경로선이 잘 보이도록 지도 위에 어두운 막을 한 겹 씌운다.
     */
    public BufferedImage renderBackdrop(TileSource tiles) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setPaint(new GradientPaint(0, 0, new Color(0x1d2b3a), 0, height, new Color(0x0d141c)));
        g.fillRect(0, 0, width, height);

        int drawn = 0;
        if (tiles != null) {
            int n = 1 << tileZoom;
            double size = TILE * scale;
            int x0 = (int) Math.floor(originX / size), x1 = (int) Math.floor((originX + width) / size);
            int y0 = (int) Math.floor(originY / size), y1 = (int) Math.floor((originY + height) / size);
            for (int ty = Math.max(0, y0); ty <= Math.min(n - 1, y1); ty++) {
                for (int tx = x0; tx <= x1; tx++) {
                    BufferedImage tile = tiles.tile(tileZoom, Math.floorMod(tx, n), ty);
                    if (tile != null) {
                        // 이웃 타일 사이 1px 틈이 생기지 않게 시작·끝을 따로 반올림한다.
                        int left = (int) Math.floor(tx * size - originX), top = (int) Math.floor(ty * size - originY);
                        int right = (int) Math.floor((tx + 1) * size - originX), bottom = (int) Math.floor((ty + 1) * size - originY);
                        g.drawImage(tile, left, top, right - left, bottom - top, null);
                        drawn++;
                    }
                }
            }
        }
        if (drawn > 0) {
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRect(0, 0, width, height);
        }
        g.dispose();
        return img;
    }

    /** 타일 공급자. 실패한 타일은 null. */
    @FunctionalInterface
    public interface TileSource {
        BufferedImage tile(int z, int x, int y);
    }

    /** 줌 0 기준 월드 픽셀(0~256). */
    static double worldX(double lng, int zoom) {
        return (lng + 180.0) / 360.0 * TILE * Math.pow(2, zoom);
    }

    static double worldY(double lat, int zoom) {
        double clamped = Math.max(-85.05112878, Math.min(85.05112878, lat));
        double s = Math.sin(Math.toRadians(clamped));
        return (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * TILE * Math.pow(2, zoom);
    }

    private static double log2(double v) {
        return Math.log(v) / Math.log(2);
    }
}
