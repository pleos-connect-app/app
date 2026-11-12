package com.randomdrive.server.recap;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MapViewTest {

    private static final List<double[]> SEOUL_TO_NAMHAN = List.of(
            new double[]{37.5547, 126.9707}, new double[]{37.4786, 127.1817});

    @Test
    void 경로가_여백_안에서_화면을_꽉_채운다() {
        MapView view = MapView.fit(SEOUL_TO_NAMHAN, 720, 1280, 150);
        Point2D.Double a = view.project(37.5547, 126.9707);
        Point2D.Double b = view.project(37.4786, 127.1817);

        for (Point2D.Double p : List.of(a, b)) {
            assertThat(p.x).isBetween(150 - 0.5, 720 - 150 + 0.5);
            assertThat(p.y).isBetween(150 - 0.5, 1280 - 150 + 0.5);
        }
        // 가로가 더 긴 경로라 가로 폭을 꽉 채운다(정수 줌이면 절반쯤만 찼다)
        assertThat(Math.abs(b.x - a.x)).isCloseTo(720 - 300, within(1.0));
        // 가운데 정렬
        assertThat((a.x + b.x) / 2).isCloseTo(360, within(0.5));
        assertThat((a.y + b.y) / 2).isCloseTo(640, within(0.5));
    }

    @Test
    void 타일은_한_단계_큰_줌으로_받아_줄여_그린다() {
        MapView view = MapView.fit(SEOUL_TO_NAMHAN, 720, 1280, 150);
        List<int[]> requested = new ArrayList<>();
        BufferedImage tile = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);

        BufferedImage img = view.renderBackdrop((z, x, y) -> {
            requested.add(new int[]{z, x, y});
            return tile;
        });

        assertThat(img.getWidth()).isEqualTo(720);
        assertThat(requested).isNotEmpty().allMatch(r -> r[0] == view.tileZoom());
    }
}
