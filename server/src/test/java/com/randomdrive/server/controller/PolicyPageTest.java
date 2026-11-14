package com.randomdrive.server.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 앱 동의 화면 QR이 여는 개인정보처리방침·위치정보 이용약관(정적 페이지). 앱 키 없이 열려야 한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PolicyPageTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 개인정보처리방침과_위치정보_이용약관이_앱_키_없이_열린다() throws Exception {
        // 정적 HTML은 Content-Type에 charset이 없어 브라우저가 <meta charset="utf-8">로 읽는다 → 테스트도 UTF-8로 읽는다.
        String body = mockMvc.perform(get("/privacy.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("<meta charset=\"utf-8\">", "개인정보처리방침", "id=\"location-terms\"");
    }

    @Test
    void 없는_정적_페이지는_404() throws Exception {
        mockMvc.perform(get("/terms-nope.html")).andExpect(status().isNotFound());
    }
}
