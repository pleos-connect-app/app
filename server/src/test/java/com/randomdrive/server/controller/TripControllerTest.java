package com.randomdrive.server.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.randomdrive.server.service.Geo;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** [2] finish, [3] recap 테스트. */
@SpringBootTest
@AutoConfigureMockMvc
class TripControllerTest {

    private static final String FINISH_BODY = """
            {
              "startedAt": 1762560000000,
              "endedAt": 1762569000000,
              "points": [
                {"lat": 37.5665, "lng": 126.9780, "ts": 1762560000000},
                {"lat": 37.7718, "lng": 128.9479, "ts": 1762569000000}
              ],
              "events": [
                {"type": "DESTINATION_SET", "ts": 1762560000000, "lat": 37.5665, "lng": 126.9780},
                {"type": "DESTINATION_ARRIVED", "ts": 1762569000000, "lat": 37.7718, "lng": 128.9479, "name": "안목해변"}
              ]
            }
            """;

    @Autowired
    MockMvc mockMvc;

    /** 테스트 준비용: 랜덤 목적지 API를 호출해 새 tripId를 받아온다. */
    private String createTrip() throws Exception {
        String json = mockMvc.perform(post("/api/v1/destinations/random")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\": 37.5665, \"lng\": 126.9780, \"radiusKm\": 100}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.tripId");
    }

    private void finish(String tripId, String expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/trips/{tripId}/finish", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FINISH_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    // ---------- [2] finish ----------

    @Test
    void finish_정상이면_202_PENDING_재전송시_현재상태_반환() throws Exception {
        String tripId = createTrip();
        finish(tripId, "PENDING");

        // recap을 한 번 조회해서 RUNNING으로 만든 뒤 다시 finish → 새로 시작하지 않고 RUNNING 그대로
        mockMvc.perform(get("/api/v1/trips/{tripId}/recap", tripId))
                .andExpect(jsonPath("$.status").value("RUNNING"));
        finish(tripId, "RUNNING");
    }

    @Test
    void finish_points가_1개면_400() throws Exception {
        String tripId = createTrip();
        mockMvc.perform(post("/api/v1/trips/{tripId}/finish", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedAt": 1, "endedAt": 2,
                                 "points": [{"lat": 37.5, "lng": 127.0, "ts": 1}],
                                 "events": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void finish_없는_tripId면_404() throws Exception {
        mockMvc.perform(post("/api/v1/trips/{tripId}/finish", "t_nope00")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FINISH_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    // ---------- [3] recap ----------

    @Test
    void recap_finish전_PENDING_이후_RUNNING_RUNNING_DONE() throws Exception {
        String tripId = createTrip();

        mockMvc.perform(get("/api/v1/trips/{tripId}/recap", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        finish(tripId, "PENDING");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/trips/{tripId}/recap", tripId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RUNNING"))
                    .andExpect(jsonPath("$.shareUrl").value(nullValue()))
                    .andExpect(jsonPath("$.videoUrl").value(nullValue()))
                    .andExpect(jsonPath("$.summary").value(nullValue()));
        }

        // 요약은 FINISH_BODY로 실제 계산: 서울시청→안목해변 두 점 직선, 9,000,000ms = 150분, 정차 없음
        double expectedKm = Math.round(Geo.distanceKm(37.5665, 126.9780, 37.7718, 128.9479) * 10) / 10.0;
        String done = mockMvc.perform(get("/api/v1/trips/{tripId}/recap", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.shareUrl").value(matchesPattern("http://localhost:8080/r/[A-Za-z0-9_-]{22}")))
                .andExpect(jsonPath("$.videoUrl").value(nullValue())) // 영상 생성(F-07) 전
                .andExpect(jsonPath("$.summary.distanceKm").value(expectedKm))
                .andExpect(jsonPath("$.summary.durationMin").value(150))
                .andExpect(jsonPath("$.summary.stopCount").value(0))
                .andReturn().getResponse().getContentAsString();

        // DONE 이후 다시 조회해도 같은 공유 링크(토큰은 한 번만 발급)
        mockMvc.perform(get("/api/v1/trips/{tripId}/recap", tripId))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.shareUrl").value((String) JsonPath.read(done, "$.shareUrl")));
    }

    @Test
    void 트립마다_공유_토큰이_다르다() throws Exception {
        String a = finishAndGetShareUrl(createTrip());
        String b = finishAndGetShareUrl(createTrip());
        org.assertj.core.api.Assertions.assertThat(a).isNotEqualTo(b);
    }

    /** 트립을 끝내고 DONE까지 조회해서 shareUrl을 돌려준다. */
    String finishAndGetShareUrl(String tripId) throws Exception {
        finish(tripId, "PENDING");
        String json = null;
        for (int i = 0; i < 3; i++) {
            json = mockMvc.perform(get("/api/v1/trips/{tripId}/recap", tripId))
                    .andReturn().getResponse().getContentAsString();
        }
        return JsonPath.read(json, "$.shareUrl");
    }

    @Test
    void recap_없는_tripId면_404() throws Exception {
        mockMvc.perform(get("/api/v1/trips/{tripId}/recap", "t_nope00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }
}
