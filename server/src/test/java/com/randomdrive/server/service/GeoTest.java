package com.randomdrive.server.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoTest {

    @Test
    void 같은_점이면_0() {
        assertThat(Geo.distanceKm(37.5665, 126.9780, 37.5665, 126.9780)).isZero();
    }

    @Test
    void 위도_1도는_약_111km() {
        assertThat(Geo.distanceKm(37.0, 127.0, 38.0, 127.0)).isCloseTo(111.19, within(0.01));
    }

    @Test
    void 서울시청에서_부산시청까지_약_325km() {
        assertThat(Geo.distanceKm(37.5665, 126.9780, 35.1796, 129.0756)).isBetween(320.0, 330.0);
    }
}
