package com.randomdrive.server.recap;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 네트워크·ffmpeg 없이 그리기만 확인한다. */
class RecapPainterTest {

    private static final RecapPainter PAINTER = new RecapPainter("");

    private static RecapScene scene(BufferedImage photo, boolean noDerivatives) {
        List<double[]> seg = new ArrayList<>();
        for (int i = 0; i <= 50; i++) {
            seg.add(new double[]{37.55 - i * 0.002, 126.98 + i * 0.004});
        }
        return new RecapScene("남한산성", "용산구 동자동", "2026년 11월 12일", "42.3km", "1시간 5분", 1, true,
                photo, noDerivatives, List.of(seg), List.of(seg.get(25)), new double[]{37.4786, 127.1817},
                List.of(new RecapScene.Waypoint("양천구 목동중앙남로 11-15", seg.get(40)[0], seg.get(40)[1])));
    }

    private static BufferedImage solid(int w, int h, Color c) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    @Test
    void 영상은_9대16_300프레임() throws Exception {
        RecapScene s = scene(null, false);
        RouteCamera plan = RouteCamera.plan(s, RecapPainter.VIDEO_W, RecapPainter.VIDEO_H,
                RecapPainter.ROUTE_FRAMES, RecapPainter.FPS);
        PerspectiveMap map = new PerspectiveMap(RecapPainter.VIDEO_W, RecapPainter.VIDEO_H, null);
        int[] frames = {0};
        PAINTER.renderVideo(s, new RecapPainter.RouteStage(plan, map, false), frame -> {
            assertThat(frame.getType()).isEqualTo(BufferedImage.TYPE_3BYTE_BGR);
            assertThat(frame.getWidth()).isEqualTo(720);
            assertThat(frame.getHeight()).isEqualTo(1280);
            frames[0]++;
        });
        assertThat(frames[0]).isEqualTo(RecapPainter.FRAME_COUNT).isEqualTo(300);
    }

    @Test
    void 썸네일은_16대9() {
        BufferedImage thumb = PAINTER.renderThumbnail(scene(solid(800, 600, Color.RED), false), null, null);
        assertThat(thumb.getWidth()).isEqualTo(1280);
        assertThat(thumb.getHeight()).isEqualTo(720);
    }

    @Test
    void 공공누리_3유형_사진은_자르지_않고_여백을_둔다() {
        // 가로로 아주 긴 사진: 3유형이면 720×720 영역 위아래에 여백, 그 외는 꽉 채움(잘림)
        BufferedImage wide = solid(1600, 400, Color.RED);
        BufferedImage keep = PAINTER.renderThumbnail(scene(wide, true), null, null);
        BufferedImage crop = PAINTER.renderThumbnail(scene(wide, false), null, null);

        int x = 1280 - 360, topY = 20;
        assertThat(isRed(keep.getRGB(x, topY))).as("3유형: 위쪽은 여백").isFalse();
        assertThat(isRed(keep.getRGB(x, 360))).as("3유형: 가운데는 사진").isTrue();
        assertThat(isRed(crop.getRGB(x, topY))).as("1유형: 꽉 채움").isTrue();
    }

    @Test
    void 사진이_없으면_경로_지도를_그린다() {
        RecapScene s = scene(null, false);
        MapView square = MapView.fit(List.of(new double[]{37.55, 126.98}, new double[]{37.45, 127.18}), 720, 720, 70);
        BufferedImage thumb = PAINTER.renderThumbnail(s, square, square.renderBackdrop(null));
        assertThat(thumb.getWidth()).isEqualTo(1280);
    }

    @Test
    void 줄바꿈은_폭을_넘지_않는다() {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        var fm = g.getFontMetrics(g.getFont().deriveFont(40f));
        List<String> lines = RecapPainter.wrap("십이선녀탕(탕수동계곡) 아주 긴 장소 이름이 들어가는 경우", fm, 300);
        assertThat(lines).hasSizeGreaterThan(1);
        lines.forEach(l -> assertThat(fm.stringWidth(l)).isLessThanOrEqualTo(300));
    }

    private static boolean isRed(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 200 && c.getGreen() < 60 && c.getBlue() < 60;
    }
}
