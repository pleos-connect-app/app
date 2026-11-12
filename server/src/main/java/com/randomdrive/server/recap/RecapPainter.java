package com.randomdrive.server.recap;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 리캡 영상 프레임과 썸네일을 그린다(Java2D). 네트워크·파일은 다루지 않는다.
 *
 * 영상(9:16, 720×1280, 30fps, 10초):
 * 1. 표지 0~2초: 목적지 사진, 날짜, 목적지 이름, "출발 동네 →"
 * 2. 경로 2~8초: 내비처럼 기울인 지도 위를 카메라가 차를 따라가며 경로선을 그린다(RouteCamera, PerspectiveMap).
 *    출발지 1.5km는 가림, 정차한 곳에 핀, 거리가 올라감
 * 3. 요약 8~10초: 도착 문구, 거리·시간·정차 횟수
 *
 * 썸네일(16:9, 1280×720): 표지와 같은 내용. 앱 결과 화면·공유 페이지 영상 포스터에 쓴다.
 *
 * 공공누리 3유형 사진은 자르거나 위에 글자·그라데이션을 얹지 않고 원본 비율로 영역 안에 넣는다.
 */
public final class RecapPainter {

    public static final int VIDEO_W = 720;
    public static final int VIDEO_H = 1280;
    public static final int THUMB_W = 1280;
    public static final int THUMB_H = 720;
    public static final int FPS = 30;

    static final double COVER_S = 2.0;
    static final double ROUTE_S = 6.0;
    static final double SUMMARY_S = 2.0;
    /** 경로 장면 프레임 수(RouteCamera.plan에 넘긴다). */
    public static final int ROUTE_FRAMES = (int) Math.round(ROUTE_S * FPS);
    static final double FADE_S = 0.4;
    public static final int FRAME_COUNT = (int) Math.round((COVER_S + ROUTE_S + SUMMARY_S) * FPS);

    private static final Color BG = new Color(0x0d141c);
    private static final Color BG_TOP = new Color(0x1d2b3a);
    private static final Color TEXT = new Color(0xf4f5f7);
    private static final Color MUTED = new Color(0xb4bcc8);
    private static final Color ROUTE = new Color(0xffc24b);
    private static final Color STOP = new Color(0xff6b6b);
    private static final Color DEST = new Color(0x4ade80);
    private static final String PHOTO_CREDIT = "사진: 한국관광공사";

    private final RecapFonts fonts;

    public RecapPainter(String fontFile) {
        this.fonts = new RecapFonts(fontFile);
    }

    /** 프레임을 하나씩 받는 곳(ffmpeg 파이프). 같은 이미지 객체를 재사용하므로 받은 즉시 써야 한다. */
    @FunctionalInterface
    public interface FrameSink {
        void accept(BufferedImage frame) throws IOException;
    }

    // ------------------------------------------------------------------ 영상

    /** 경로 장면을 그릴 재료: 카메라 계획, 기울인 지도, 지도 타일이 실제로 들어갔는지(출처 표시용). */
    public record RouteStage(RouteCamera plan, PerspectiveMap map, boolean mapTiles) {
    }

    public void renderVideo(RecapScene scene, RouteStage stage, FrameSink sink) throws IOException {
        RouteCamera plan = stage.plan();
        BufferedImage cover = renderCover(scene);
        int routeFrames = plan.frames();
        int last = routeFrames - 1;
        BufferedImage routeEnd = new BufferedImage(VIDEO_W, VIDEO_H, BufferedImage.TYPE_INT_RGB);
        paintRoute(routeEnd, scene, stage, plan.camera(last), plan.progress(last), true);
        // 요약 배경은 글자 없는 완성 경로(위 제목·거리가 요약 글자 뒤로 비치지 않게)
        BufferedImage bare = new BufferedImage(VIDEO_W, VIDEO_H, BufferedImage.TYPE_INT_RGB);
        paintRoute(bare, scene, stage, plan.camera(last), 1.0, false);
        BufferedImage summaryBg = darken(bare, 175);

        BufferedImage frame = new BufferedImage(VIDEO_W, VIDEO_H, BufferedImage.TYPE_3BYTE_BGR);
        BufferedImage routeFrame = new BufferedImage(VIDEO_W, VIDEO_H, BufferedImage.TYPE_INT_RGB);
        BufferedImage summaryFrame = new BufferedImage(VIDEO_W, VIDEO_H, BufferedImage.TYPE_INT_RGB);
        int coverFrames = (int) Math.round(COVER_S * FPS);
        for (int i = 0; i < FRAME_COUNT; i++) {
            double t = i / (double) FPS;
            Graphics2D g = frame.createGraphics();
            if (i < coverFrames) {
                g.drawImage(cover, 0, 0, null);
                fadeFromBlack(g, t / FADE_S);
            } else if (i < coverFrames + routeFrames) {
                int r = i - coverFrames;
                paintRoute(routeFrame, scene, stage, plan.camera(r), plan.progress(r), true);
                g.drawImage(routeFrame, 0, 0, null);
                crossfade(g, cover, 1 - (r / (double) FPS) / FADE_S);
            } else {
                double local = (i - coverFrames - routeFrames) / (double) FPS;
                paintSummary(summaryFrame, scene, summaryBg, stage.mapTiles(), clamp(local / 1.0));
                g.drawImage(summaryFrame, 0, 0, null);
                crossfade(g, routeEnd, 1 - local / FADE_S);
            }
            g.dispose();
            sink.accept(frame);
        }
    }

    BufferedImage renderCover(RecapScene scene) {
        BufferedImage img = new BufferedImage(VIDEO_W, VIDEO_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(img);
        g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, VIDEO_H, BG));
        g.fillRect(0, 0, VIDEO_W, VIDEO_H);

        int photoH = 760;
        boolean photo = drawPhoto(g, scene, 0, 0, VIDEO_W, photoH);
        if (!photo) {
            drawBrandMark(g, 0, 0, VIDEO_W, photoH);
        }

        int x = 56, w = VIDEO_W - 112;
        int y = photoH + 64;
        y = drawText(g, "RANDOM DRIVE · " + scene.dateText(), fonts.bold(26), MUTED, x, y, w, 1, 26) + 18;
        y = drawText(g, scene.placeName(), fonts.bold(68), TEXT, x, y, w, 2, 44) + 20;
        if (scene.originName() != null) {
            drawText(g, scene.originName() + " →", fonts.regular(32), MUTED, x, y, w, 1, 24);
        }
        if (photo) {
            drawCredit(g, PHOTO_CREDIT, VIDEO_W - 24, photoH - 16, scene.photoNoDerivatives() ? MUTED : TEXT);
        }
        g.dispose();
        return img;
    }

    /** 경로 장면 한 프레임: 기울인 지도 → 지나온 경로선 → 정차·목적지 핀 → 차 화살표 → 글자. */
    private void paintRoute(BufferedImage target, RecapScene scene, RouteStage stage, Camera cam, double progress,
                            boolean withText) {
        int[] pixels = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
        stage.map().renderGround(cam, pixels);
        RouteCamera plan = stage.plan();
        PerspectiveMap map = stage.map();

        Graphics2D g = graphics(target);
        // 지도 위 어두운 막(경로선이 잘 보이게), 기울었을 때 먼 쪽은 더 어둡게(안개)
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(0, 0, VIDEO_W, VIDEO_H);
        if (cam.pitch() > 0.05) {
            int fogAlpha = (int) Math.round(200 * Math.min(1, cam.pitch() / RouteCamera.PITCH));
            g.setPaint(new GradientPaint(0, 0, new Color(13, 20, 28, fogAlpha),
                    0, VIDEO_H * 0.45f, new Color(13, 20, 28, 0)));
            g.fillRect(0, 0, VIDEO_W, (int) (VIDEO_H * 0.45));
        }
        // 위·아래 글자가 지도와 겹쳐도 읽히게
        g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 170), 0, 300, new Color(0, 0, 0, 0)));
        g.fillRect(0, 0, VIDEO_W, 300);
        g.setPaint(new GradientPaint(0, VIDEO_H - 140, new Color(0, 0, 0, 0), 0, VIDEO_H, new Color(0, 0, 0, 170)));
        g.fillRect(0, VIDEO_H - 140, VIDEO_W, 140);

        double drawnLen = plan.totalLength * progress;
        drawRoute3d(g, plan, map, cam, drawnLen);

        for (double[] s : scene.stops()) {
            double[] w = RouteCamera.world(s);
            if (plan.nearestAlong(w) <= drawnLen) {
                drawPin3d(g, map.project(cam, w[0], w[1]), STOP, 13);
            }
        }
        double[] dest = RouteCamera.world(scene.destination());
        drawPin3d(g, map.project(cam, dest[0], dest[1]), DEST, progress >= 1.0 && scene.arrived() ? 20 : 15);

        if (plan.totalLength > 0 && progress < 1.0) {
            double[] head = plan.pointAt(drawnLen);
            double[] ahead = plan.pointAt(Math.min(plan.totalLength, drawnLen + plan.totalLength * 0.01));
            PerspectiveMap.Projected h = map.project(cam, head[0], head[1]);
            PerspectiveMap.Projected a = map.project(cam, ahead[0], ahead[1]);
            if (h != null) {
                double angle = a == null || a.point().distance(h.point()) < 0.5
                        ? -Math.PI / 2 : Math.atan2(a.y() - h.y(), a.x() - h.x());
                drawCar(g, h.x(), h.y(), angle);
            }
        }

        if (withText) {
            int x = 56, w = VIDEO_W - 112;
            int y = drawText(g, scene.journeyText(), fonts.bold(34), TEXT, x, 72, w, 2, 26) + 10;
            double km = parseKm(scene.distanceText()) * progress;
            drawText(g, String.format(Locale.ROOT, "%.1fkm", km), fonts.bold(60), ROUTE, x, y, w, 1, 60);
        }
        if (stage.mapTiles()) {
            drawCredit(g, VWorldClient.ATTRIBUTION, VIDEO_W - 24, VIDEO_H - 28, MUTED);
        }
        g.dispose();
    }

    /** 지나온 경로선. 가까이 보일수록 조금 굵게. 카메라 뒤로 넘어간 점에서는 선을 끊는다. */
    private static void drawRoute3d(Graphics2D g, RouteCamera plan, PerspectiveMap map, Camera cam, double length) {
        List<Path2D.Double> paths = new ArrayList<>();
        double widthScale = 0;
        int counted = 0;
        for (int k = 0; k < plan.polylines.size(); k++) {
            double[][] pts = plan.polylines.get(k);
            double[] cum = plan.cumulative.get(k);
            if (cum[0] >= length) {
                break;
            }
            Path2D.Double path = null;
            PerspectiveMap.Projected prev = null;
            for (int i = 0; i < pts.length; i++) {
                double[] p = pts[i];
                boolean partial = false;
                if (cum[i] > length) {
                    double f = (length - cum[i - 1]) / (cum[i] - cum[i - 1]);
                    p = new double[]{pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * f,
                            pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * f};
                    partial = true;
                }
                PerspectiveMap.Projected q = map.project(cam, p[0], p[1]);
                if (q == null) {
                    path = null;
                    prev = null;
                } else if (path == null) {
                    path = new Path2D.Double();
                    path.moveTo(q.x(), q.y());
                    paths.add(path);
                    prev = q;
                } else if (partial || Math.abs(q.x() - prev.x()) + Math.abs(q.y() - prev.y()) >= 1.5) {
                    path.lineTo(q.x(), q.y());
                    prev = q;
                }
                if (q != null) {
                    widthScale += Math.min(q.scale(), 2.5);
                    counted++;
                }
                if (partial) {
                    break;
                }
            }
        }
        double s = counted == 0 ? 1 : Math.max(0.7, Math.min(1.6, widthScale / counted));
        BasicStroke glow = new BasicStroke((float) (16 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        BasicStroke core = new BasicStroke((float) (7 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        for (Path2D.Double path : paths) {
            g.setStroke(glow);
            g.setColor(new Color(255, 194, 75, 70));
            g.draw(path);
            g.setStroke(core);
            g.setColor(ROUTE);
            g.draw(path);
        }
    }

    /** 땅에 꽂힌 핀. 원근 배율만큼 멀면 작게. 화면 밖이면 그리지 않는다. */
    private static void drawPin3d(Graphics2D g, PerspectiveMap.Projected p, Color color, double r) {
        if (p == null || p.y() < -40 || p.y() > VIDEO_H + 40 || p.x() < -40 || p.x() > VIDEO_W + 40) {
            return;
        }
        double k = Math.max(0.6, Math.min(1.4, p.scale()));
        drawPin(g, new Point2D.Double(p.x(), p.y()), color, r * k);
    }

    /** 내비처럼 진행 방향을 가리키는 차 화살표. angle은 화면 기준(0 = 오른쪽). */
    private static void drawCar(Graphics2D g, double x, double y, double angle) {
        AffineTransform saved = g.getTransform();
        g.translate(x, y);
        g.rotate(angle + Math.PI / 2);
        g.setColor(new Color(255, 194, 75, 90));
        g.fill(new Ellipse2D.Double(-30, -30, 60, 60));
        Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(0, -22);
        arrow.lineTo(16, 16);
        arrow.lineTo(0, 7);
        arrow.lineTo(-16, 16);
        arrow.closePath();
        g.translate(0, 3);
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(arrow);
        g.translate(0, -3);
        g.setColor(Color.WHITE);
        g.fill(arrow);
        g.setTransform(saved);
    }

    private void paintSummary(BufferedImage target, RecapScene scene, BufferedImage bg, boolean mapTiles, double countUp) {
        Graphics2D g = graphics(target);
        g.drawImage(bg, 0, 0, null);
        int x = 56, w = VIDEO_W - 112;
        int y = 260;
        String headline = scene.arrived()
                ? scene.placeName() + "에 도착했어요"
                : scene.placeName() + KoreanParticle.euro(scene.placeName()) + " 떠난 드라이브";
        y = drawText(g, headline, fonts.bold(56), TEXT, x, y, w, 3, 36) + 72;

        double eased = easeOut(countUp);
        y = drawStat(g, "이동 거리", String.format(java.util.Locale.ROOT, "%.1fkm", parseKm(scene.distanceText()) * eased), x, y, w) + 40;
        y = drawStat(g, "주행 시간", scene.durationText(), x, y, w) + 40;
        drawStat(g, "예상 밖 정차", Math.round(scene.stopCount() * eased) + "번", x, y, w);

        drawText(g, "RandomDrive", fonts.bold(30), ROUTE, x, VIDEO_H - 130, w, 1, 30);
        if (mapTiles) {
            drawCredit(g, VWorldClient.ATTRIBUTION, VIDEO_W - 24, VIDEO_H - 28, MUTED);
        }
        g.dispose();
    }

    private int drawStat(Graphics2D g, String label, String value, int x, int y, int w) {
        y = drawText(g, label, fonts.regular(28), MUTED, x, y, w, 1, 24) + 4;
        return drawText(g, value, fonts.bold(72), TEXT, x, y, w, 1, 48);
    }

    // ------------------------------------------------------------------ 썸네일

    /** 16:9 썸네일. 오른쪽에 사진(없으면 경로 지도), 왼쪽에 글자. */
    public BufferedImage renderThumbnail(RecapScene scene, MapView squareMap, BufferedImage squareBackdrop) {
        BufferedImage img = new BufferedImage(THUMB_W, THUMB_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(img);
        g.setPaint(new GradientPaint(0, 0, BG_TOP, THUMB_W, THUMB_H, BG));
        g.fillRect(0, 0, THUMB_W, THUMB_H);

        int px = THUMB_W - THUMB_H; // 오른쪽 720×720
        boolean photo = drawPhoto(g, scene, px, 0, THUMB_H, THUMB_H);
        if (!photo && squareBackdrop != null) {
            g.drawImage(squareBackdrop, px, 0, null);
            RouteGeometry geo = RouteGeometry.of(scene, squareMap);
            g.translate(px, 0);
            g.setClip(0, 0, THUMB_H, THUMB_H);
            geo.drawRoute(g, geo.totalLength);
            geo.stops.forEach(stop -> drawPin(g, stop.point, STOP, 11));
            drawPin(g, geo.destination, DEST, 15);
            g.setClip(null);
            g.translate(-px, 0);
        } else if (!photo) {
            drawBrandMark(g, px, 0, THUMB_H, THUMB_H);
        }

        int x = 56, w = px - 100;
        int y = 80;
        y = drawText(g, "RANDOM DRIVE · " + scene.dateText(), fonts.bold(24), MUTED, x, y, w, 1, 18) + 20;
        y = drawText(g, scene.placeName(), fonts.bold(60), TEXT, x, y, w, 3, 36) + 18;
        if (scene.originName() != null) {
            drawText(g, scene.originName() + " →", fonts.regular(28), MUTED, x, y, w, 1, 20);
        }

        int sy = THUMB_H - 200;
        // "2시간 30분"이 가장 길어 시간 칸을 넓게
        int c1 = (int) (w * 0.33), c2 = (int) (w * 0.45);
        drawThumbStat(g, "거리", scene.distanceText(), x, sy, c1 - 12);
        drawThumbStat(g, "시간", scene.durationText(), x + c1, sy, c2 - 12);
        drawThumbStat(g, "정차", scene.stopCount() + "번", x + c1 + c2, sy, w - c1 - c2);

        if (photo) {
            drawCredit(g, PHOTO_CREDIT, THUMB_W - 20, THUMB_H - 16, scene.photoNoDerivatives() ? MUTED : TEXT);
        }
        g.dispose();
        return img;
    }

    private void drawThumbStat(Graphics2D g, String label, String value, int x, int y, int w) {
        y = drawText(g, label, fonts.regular(24), MUTED, x, y, w, 1, 18) + 6;
        drawText(g, value, fonts.bold(40), TEXT, x, y, w, 1, 22);
    }

    // ------------------------------------------------------------------ 그리기 도구

    /**
     * 사진을 영역에 넣는다. 3유형이면 원본 비율 그대로(여백은 배경색), 그 외는 영역을 꽉 채우게 자른다.
     * 사진이 없으면 false.
     */
    private boolean drawPhoto(Graphics2D g, RecapScene scene, int x, int y, int w, int h) {
        BufferedImage photo = scene.photo();
        if (photo == null || photo.getWidth() <= 0 || photo.getHeight() <= 0) {
            return false;
        }
        double sx = w / (double) photo.getWidth(), sy = h / (double) photo.getHeight();
        double s = scene.photoNoDerivatives() ? Math.min(sx, sy) : Math.max(sx, sy);
        int dw = (int) Math.round(photo.getWidth() * s), dh = (int) Math.round(photo.getHeight() * s);
        java.awt.Shape oldClip = g.getClip();
        g.clipRect(x, y, w, h);
        g.setColor(BG);
        g.fillRect(x, y, w, h);
        g.drawImage(photo, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
        g.setClip(oldClip);
        return true;
    }

    private void drawBrandMark(Graphics2D g, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, new Color(0x274060), x + w, y + h, new Color(0x0f1a26)));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(255, 194, 75, 60));
        g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D road = new Path2D.Double();
        road.moveTo(x + w * 0.15, y + h * 0.85);
        road.curveTo(x + w * 0.45, y + h * 0.75, x + w * 0.3, y + h * 0.35, x + w * 0.8, y + h * 0.2);
        g.draw(road);
        drawPin(g, new Point2D.Double(x + w * 0.8, y + h * 0.2), DEST, 22);
    }

    private static void drawPin(Graphics2D g, Point2D.Double p, Color color, double r) {
        if (p == null) {
            return;
        }
        g.setColor(new Color(0, 0, 0, 110));
        g.fill(new Ellipse2D.Double(p.x - r - 3, p.y - r - 1, 2 * r + 6, 2 * r + 6));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(p.x - r, p.y - r, 2 * r, 2 * r));
        g.setColor(color);
        g.fill(new Ellipse2D.Double(p.x - r + 4, p.y - r + 4, 2 * r - 8, 2 * r - 8));
    }

    private void drawCredit(Graphics2D g, String text, int right, int baseline, Color color) {
        g.setFont(fonts.regular(18));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text);
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(right - w - 12, baseline - fm.getAscent() - 4, w + 16, fm.getAscent() + fm.getDescent() + 8, 10, 10);
        g.setColor(color);
        g.drawString(text, right - w - 4, baseline);
    }

    /**
     * 글자를 maxLines 줄 안에 넣는다. 안 들어가면 minSize까지 줄이고, 그래도 넘치면 마지막 줄을 "…"로 자른다.
     * top은 첫 줄 윗부분 y. 반환: 마지막 줄 아랫부분 y.
     */
    int drawText(Graphics2D g, String text, Font font, Color color, int x, int top, int maxWidth, int maxLines, int minSize) {
        Font f = font;
        List<String> lines = wrap(text, g.getFontMetrics(f), maxWidth);
        while (lines.size() > maxLines && f.getSize2D() > minSize) {
            f = f.deriveFont(Math.max(minSize, f.getSize2D() - 2));
            lines = wrap(text, g.getFontMetrics(f), maxWidth);
        }
        FontMetrics fm = g.getFontMetrics(f);
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
            String last = lines.get(maxLines - 1);
            while (!last.isEmpty() && fm.stringWidth(last + "…") > maxWidth) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(maxLines - 1, last + "…");
        }
        g.setFont(f);
        g.setColor(color);
        int lineH = (int) Math.ceil(f.getSize2D() * 1.25);
        int y = top;
        for (String line : lines) {
            g.drawString(line, x, y + fm.getAscent());
            y += lineH;
        }
        return y;
    }

    /** 띄어쓰기 단위로 줄을 나누고, 한 단어가 너무 길면 글자 단위로 나눈다. */
    static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            for (char c : word.toCharArray()) {
                if (!line.isEmpty() && fm.stringWidth(line.toString() + c) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(c);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static BufferedImage darken(BufferedImage src, int alpha) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.setColor(new Color(13, 20, 28, alpha));
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.dispose();
        return out;
    }

    private static void crossfade(Graphics2D g, BufferedImage previous, double alpha) {
        if (alpha <= 0) {
            return;
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp(alpha)));
        g.drawImage(previous, 0, 0, null);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static void fadeFromBlack(Graphics2D g, double progress) {
        if (progress >= 1) {
            return;
        }
        g.setColor(new Color(0, 0, 0, (int) Math.round(255 * (1 - clamp(progress)))));
        g.fillRect(0, 0, VIDEO_W, VIDEO_H);
    }

    static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    static double easeInOut(double t) {
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    }

    static double easeOut(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    /** "123.4km" → 123.4. 형식이 다르면 0. */
    static double parseKm(String text) {
        try {
            return Double.parseDouble(text.replace("km", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ 경로 좌표 → 화면

    /** 화면 좌표로 바꾼 경로와 누적 길이. 프레임마다 다시 계산하지 않도록 한 번만 만든다. */
    static final class RouteGeometry {
        record Pin(Point2D.Double point, double along) {
        }

        final List<List<Point2D.Double>> polylines = new ArrayList<>();
        final List<double[]> cumulative = new ArrayList<>();
        final List<Pin> stops = new ArrayList<>();
        Point2D.Double destination;
        double totalLength;

        static RouteGeometry of(RecapScene scene, MapView map) {
            RouteGeometry geo = new RouteGeometry();
            double run = 0;
            for (List<double[]> seg : scene.route()) {
                List<Point2D.Double> pts = new ArrayList<>();
                for (double[] ll : seg) {
                    Point2D.Double p = map.project(ll[0], ll[1]);
                    // 화면에서 1.5px도 안 움직이는 점은 버린다(1만 점 경로도 가볍게)
                    if (pts.isEmpty() || pts.get(pts.size() - 1).distance(p) >= 1.5) {
                        pts.add(p);
                    }
                }
                if (pts.size() < 2) {
                    continue;
                }
                double[] cum = new double[pts.size()];
                cum[0] = run;
                for (int i = 1; i < pts.size(); i++) {
                    run += pts.get(i - 1).distance(pts.get(i));
                    cum[i] = run;
                }
                geo.polylines.add(pts);
                geo.cumulative.add(cum);
            }
            geo.totalLength = run;
            for (double[] s : scene.stops()) {
                Point2D.Double p = map.project(s[0], s[1]);
                geo.stops.add(new Pin(p, geo.nearestAlong(p)));
            }
            geo.destination = map.project(scene.destination()[0], scene.destination()[1]);
            return geo;
        }

        /** 경로에서 p와 가장 가까운 점까지의 누적 길이. 경로가 없으면 0. */
        double nearestAlong(Point2D.Double p) {
            double best = Double.MAX_VALUE, along = 0;
            for (int s = 0; s < polylines.size(); s++) {
                List<Point2D.Double> pts = polylines.get(s);
                for (int i = 0; i < pts.size(); i++) {
                    double d = pts.get(i).distanceSq(p);
                    if (d < best) {
                        best = d;
                        along = cumulative.get(s)[i];
                    }
                }
            }
            return along;
        }

        /** 처음부터 length만큼 그리고, 그린 끝 점(차 위치)을 돌려준다. 그릴 게 없으면 null. */
        Point2D.Double drawRoute(Graphics2D g, double length) {
            Point2D.Double head = null;
            BasicStroke glow = new BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            BasicStroke core = new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            for (int s = 0; s < polylines.size(); s++) {
                List<Point2D.Double> pts = polylines.get(s);
                double[] cum = cumulative.get(s);
                if (cum[0] >= length) {
                    break;
                }
                Path2D.Double path = new Path2D.Double();
                path.moveTo(pts.get(0).x, pts.get(0).y);
                head = pts.get(0);
                for (int i = 1; i < pts.size(); i++) {
                    if (cum[i] <= length) {
                        path.lineTo(pts.get(i).x, pts.get(i).y);
                        head = pts.get(i);
                    } else {
                        double f = (length - cum[i - 1]) / (cum[i] - cum[i - 1]);
                        Point2D.Double a = pts.get(i - 1), b = pts.get(i);
                        head = new Point2D.Double(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f);
                        path.lineTo(head.x, head.y);
                        break;
                    }
                }
                g.setStroke(glow);
                g.setColor(new Color(255, 194, 75, 70));
                g.draw(path);
                g.setStroke(core);
                g.setColor(ROUTE);
                g.draw(path);
            }
            return head;
        }
    }
}
