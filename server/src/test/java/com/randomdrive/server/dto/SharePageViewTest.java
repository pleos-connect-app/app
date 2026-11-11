package com.randomdrive.server.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SharePageViewTest {

    @Test
    void 시간_표기() {
        assertThat(SharePageView.formatMinutes(0)).isEqualTo("0분");
        assertThat(SharePageView.formatMinutes(59)).isEqualTo("59분");
        assertThat(SharePageView.formatMinutes(60)).isEqualTo("1시간");
        assertThat(SharePageView.formatMinutes(61)).isEqualTo("1시간 1분");
        assertThat(SharePageView.formatMinutes(150)).isEqualTo("2시간 30분");
    }

    @Test
    void 거리_표기는_소수_첫째_자리() {
        assertThat(SharePageView.formatKm(214.6)).isEqualTo("214.6km");
        assertThat(SharePageView.formatKm(3.0)).isEqualTo("3.0km");
    }

    @Test
    void 공공누리_3유형이면_원본_비율_표시() {
        DestinationDto d = new DestinationDto("p", "장소", 37.0, 127.0, "주소", "https://img", 1.0);
        RecapSummaryDto s = new RecapSummaryDto(1.0, 1, 0);

        assertThat(SharePageView.of(d, "Type3", 0L, s, null, "u").imageNoDerivatives()).isTrue();
        assertThat(SharePageView.of(d, "Type1", 0L, s, null, "u").imageNoDerivatives()).isFalse();
        assertThat(SharePageView.of(d, null, 0L, s, null, "u").imageNoDerivatives()).isFalse();
    }
}
