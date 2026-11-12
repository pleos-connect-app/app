package com.randomdrive.server.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /r/{token} 공유 페이지 (F-08). */
@SpringBootTest
@AutoConfigureMockMvc
class ShareControllerTest {

    /** 출발지(서울역)와 경로 좌표. 공유 페이지에 이 값들이 나오면 안 된다. */
    static final String FINISH_BODY = """
            {
              "startedAt": 1794249070574,
              "endedAt": 1794254470574,
              "points": [
                {"lat": 37.554698, "lng": 126.970698, "ts": 1794249070574},
                {"lat": 37.520011, "lng": 127.080022, "ts": 1794251770574},
                {"lat": 37.478600, "lng": 127.181700, "ts": 1794254470574}
              ],
              "events": []
            }
            """;

    @Autowired
    MockMvc mockMvc;

    /** 목적지 발급 → finish → recap DONE까지 진행하고 shareUrl을 돌려준다. */
    static String completeTrip(MockMvc mockMvc, String destinationBody) throws Exception {
        String created = mockMvc.perform(post("/api/v1/destinations/random")
                        .contentType(MediaType.APPLICATION_JSON).content(destinationBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tripId = JsonPath.read(created, "$.tripId");
        mockMvc.perform(post("/api/v1/trips/{tripId}/finish", tripId)
                        .contentType(MediaType.APPLICATION_JSON).content(FINISH_BODY))
                .andExpect(status().isAccepted());
        // 테스트 설정(recap.workers=0)에서는 finish 안에서 생성이 끝나 바로 DONE
        String recap = mockMvc.perform(get("/api/v1/trips/{tripId}/recap", tripId))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(recap, "$.shareUrl");
    }

    @Test
    void 공유_페이지에_장소와_요약이_나오고_위치_로그는_나오지_않는다() throws Exception {
        String shareUrl = completeTrip(mockMvc,
                "{\"lat\": 37.554698, \"lng\": 126.970698, \"radiusKm\": 50, \"demo\": true}");

        mockMvc.perform(get(URI.create(shareUrl).getPath()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(content().string(containsString("<h1>남한산성</h1>")))
                .andExpect(content().string(containsString("경기도 광주시 남한산성면")))
                .andExpect(content().string(containsString("2026년 11월 10일")))  // endedAt, 한국 시간
                .andExpect(content().string(containsString("1시간 30분")))       // 5,400,000ms
                .andExpect(content().string(containsString("예상 밖 정차")))
                .andExpect(content().string(containsString("리캡 영상은 준비 중이에요")))
                .andExpect(content().string(containsString("data-url=\"" + shareUrl + "\"")))
                // 출발지·경로 좌표(원본 위치 로그)는 노출 금지
                .andExpect(content().string(not(containsString("37.554698"))))
                .andExpect(content().string(not(containsString("126.970698"))))
                .andExpect(content().string(not(containsString("127.080022"))));
    }

    @Test
    void 이미지가_있는_장소면_사진과_출처를_보여준다() throws Exception {
        String shareUrl = completeTrip(mockMvc, "{\"lat\": 37.554698, \"lng\": 126.970698, \"radiusKm\": 50}");

        mockMvc.perform(get(URI.create(shareUrl).getPath()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<img src=\"https://")))
                .andExpect(content().string(containsString("사진: 한국관광공사")));
    }

    @Test
    void 썸네일은_공유_토큰_주소로_내려가고_영상이_없으면_404() throws Exception {
        String shareUrl = completeTrip(mockMvc,
                "{\"lat\": 37.554698, \"lng\": 126.970698, \"radiusKm\": 50, \"demo\": true}");
        String path = URI.create(shareUrl).getPath();

        byte[] jpeg = mockMvc.perform(get(path + "/thumbnail.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andReturn().getResponse().getContentAsByteArray();
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
        org.assertj.core.api.Assertions.assertThat(img.getWidth()).isEqualTo(1280);
        org.assertj.core.api.Assertions.assertThat(img.getHeight()).isEqualTo(720);

        // 테스트 설정은 recap.video=false → 영상 없음
        mockMvc.perform(get(path + "/video.mp4")).andExpect(status().isNotFound());
        // 공유 페이지의 미리보기 이미지는 썸네일
        mockMvc.perform(get(path))
                .andExpect(content().string(containsString("og:image\" content=\"" + shareUrl + "/thumbnail.jpg\"")));
    }

    @Test
    void 없는_토큰의_미디어는_404() throws Exception {
        mockMvc.perform(get("/r/{token}/thumbnail.jpg", "AAAAAAAAAAAAAAAAAAAAAA")).andExpect(status().isNotFound());
        mockMvc.perform(get("/r/{token}/video.mp4", "..%2F..%2Fetc")).andExpect(status().isNotFound());
    }

    @Test
    void 없는_토큰이면_404_안내_페이지() throws Exception {
        mockMvc.perform(get("/r/{token}", "AAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("링크를 찾을 수 없어요")));
    }
}
