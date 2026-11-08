package com.randomdrive.server.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

        mockMvc.perform(get("/api/v1/trips/{tripId}/recap", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.shareUrl").value("http://localhost:8080/r/demo123"))
                .andExpect(jsonPath("$.videoUrl").value("http://localhost:8080/videos/sample.mp4"))
                .andExpect(jsonPath("$.summary.distanceKm").value(214.6))
                .andExpect(jsonPath("$.summary.durationMin").value(150))
                .andExpect(jsonPath("$.summary.stopCount").value(2));
    }

    @Test
    void recap_없는_tripId면_404() throws Exception {
        mockMvc.perform(get("/api/v1/trips/{tripId}/recap", "t_nope00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }
}
