package com.randomdrive.server.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 설정값을 바꿨을 때: 공개 주소(PUBLIC_BASE_URL, 끝의 / 처리)와 만료(ttl=0 → 즉시 만료 → 410).
 * 설정이 다른 별도 스프링 컨텍스트라 테스트 클래스를 나눴다.
 */
@SpringBootTest(properties = {
        "share.public-base-url=https://rd.example.com/",
        "share.ttl=0s"
})
@AutoConfigureMockMvc
class SharePropertiesOverrideTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void shareUrl은_공개_주소를_쓰고_기간이_지난_링크는_410() throws Exception {
        String shareUrl = ShareControllerTest.completeTrip(mockMvc,
                "{\"lat\": 37.554698, \"lng\": 126.970698, \"radiusKm\": 50, \"demo\": true}");

        assertThat(shareUrl).matches("https://rd\\.example\\.com/r/[A-Za-z0-9_-]{22}");

        mockMvc.perform(get(URI.create(shareUrl).getPath()))
                .andExpect(status().isGone())
                .andExpect(content().string(containsString("공유 기간이 끝났어요")));
    }
}
