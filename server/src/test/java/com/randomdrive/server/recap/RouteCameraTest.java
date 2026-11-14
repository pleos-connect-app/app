package com.randomdrive.server.recap;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 경로 장면 카메라: 전체 보기 → 기울여 따라가기 → 전체 보기, 그리고 원근 투영이 맞는지. */
class RouteCameraTest {

    private static final int W = 720, H = 1280, FRAMES = 180;

    /** 서쪽에서 동쪽으로 가는 직선 경로(약 30km). */
    private static RecapScene eastbound() {
        List<double[]> seg = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            seg.add(new double[]{37.4, 127.0 + i * 0.0034});
        }
        return new RecapScene("목적지", null, "", "30.0km", "1시간", 0, true, null, false,
                List.of(seg), List.of(), seg.get(seg.size() - 1), List.of());
    }

    @Test
    void 처음과_끝은_위에서_본_전체_보기_중간은_기울여_따라간다() {
        RouteCamera plan = RouteCamera.plan(eastbound(), W, H, FRAMES, 30);

        assertThat(plan.camera(0).pitch()).isZero();
        assertThat(plan.camera(FRAMES - 1).pitch()).isCloseTo(0, within(1e-6));
        assertThat(plan.progress(0)).isZero();
        assertThat(plan.progress(FRAMES - 1)).isCloseTo(1, within(1e-6));

        Camera mid = plan.camera(FRAMES / 2);
        assertThat(mid.pitch()).isCloseTo(RouteCamera.PITCH, within(1e-6));
        assertThat(mid.zoom()).isGreaterThan(plan.overview.zoom());
        // 동쪽으로 가므로 화면 위가 동쪽(방위 90도)
        assertThat(Math.toDegrees(mid.bearing())).isCloseTo(90, within(5.0));
    }

    @Test
    void 따라가는_카메라에서_차는_화면_아래쪽_가운데_앞쪽_경로는_위로() {
        RouteCamera plan = RouteCamera.plan(eastbound(), W, H, FRAMES, 30);
        PerspectiveMap map = new PerspectiveMap(W, H, null);
        int f = FRAMES / 2;
        Camera cam = plan.camera(f);
        double s = plan.progress(f) * plan.totalLength;

        PerspectiveMap.Projected car = map.project(cam, cam.centerX(), cam.centerY());
        assertThat(car.x()).isCloseTo(W / 2.0, within(0.5));
        assertThat(car.y()).isCloseTo(H * PerspectiveMap.TARGET_Y, within(0.5));

        double[] ahead = plan.pointAt(s + plan.totalLength * 0.05);
        PerspectiveMap.Projected a = map.project(cam, ahead[0], ahead[1]);
        assertThat(a.y()).isLessThan(car.y());
        assertThat(a.x()).isCloseTo(W / 2.0, within(40.0));
        assertThat(a.scale()).as("앞쪽(먼 곳)은 작게").isLessThan(1.0);
    }

    @Test
    void 전체_보기에서는_경로가_화면_안에_들어온다() {
        RouteCamera plan = RouteCamera.plan(eastbound(), W, H, FRAMES, 30);
        PerspectiveMap map = new PerspectiveMap(W, H, null);
        for (double s : new double[]{0, plan.totalLength / 2, plan.totalLength}) {
            double[] p = plan.pointAt(s);
            PerspectiveMap.Projected q = map.project(plan.camera(0), p[0], p[1]);
            assertThat(q.x()).isBetween(0.0, (double) W);
            assertThat(q.y()).isBetween(0.0, (double) H);
        }
    }

    @Test
    void 바닥_렌더링은_타일이_없으면_격자로_채운다() {
        RouteCamera plan = RouteCamera.plan(eastbound(), W, H, FRAMES, 30);
        PerspectiveMap map = new PerspectiveMap(W, H, null);
        int[] px = new int[W * H];
        map.renderGround(plan.camera(FRAMES / 2), px);
        assertThat(java.util.Arrays.stream(px).distinct().count()).isGreaterThanOrEqualTo(2);
    }
}
