package com.randomdrive.server.recap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 역지오코딩 응답 해석(앱 VWorldGeocoder.parse와 같은 규칙). 네트워크는 쓰지 않는다. */
class VWorldClientTest {

    private final VWorldClient client = new VWorldClient("");

    @Test
    void 구와_동을_잇는다() throws Exception {
        String body = """
                {"response":{"status":"OK","result":[{"structure":
                  {"level1":"서울특별시","level2":"용산구","level3":"","level4L":"동자동"}}]}}
                """;
        assertThat(client.parseAreaName(body)).isEqualTo("용산구 동자동");
    }

    @Test
    void 결과가_없으면_null() throws Exception {
        assertThat(client.parseAreaName("{\"response\":{\"status\":\"NOT_FOUND\"}}")).isNull();
    }

    @Test
    void 키가_없으면_호출하지_않는다() {
        assertThat(client.enabled()).isFalse();
        assertThat(client.areaName(37.5, 127.0)).isNull();
        assertThat(client.satelliteTile(10, 1, 1)).isNull();
    }
}
