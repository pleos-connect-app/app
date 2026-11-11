package com.randomdrive.server.controller;

import com.jayway.jsonpath.JsonPath;
import com.randomdrive.server.service.Candidate;
import com.randomdrive.server.service.CandidateRepository;
import com.randomdrive.server.service.Geo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** [1] POST /api/v1/destinations/random 테스트. */
@SpringBootTest
@AutoConfigureMockMvc
class DestinationControllerTest {

    /** 에뮬레이터 기본 위치(서울역). 앱이 서버 연동 확인 때 쓴 좌표. */
    private static final double SEOUL_LAT = 37.5547;
    private static final double SEOUL_LNG = 126.9707;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CandidateRepository candidateRepository;

    private String requestBody(double lat, double lng, int radiusKm, Boolean demo) {
        String demoField = demo == null ? "" : ", \"demo\": " + demo;
        return "{\"lat\": " + lat + ", \"lng\": " + lng + ", \"radiusKm\": " + radiusKm + demoField + "}";
    }

    @Test
    void 랜덤_목적지는_반경_안_후보이고_응답값이_후보와_일치한다() throws Exception {
        Map<String, Candidate> byId = candidateRepository.findAll().stream()
                .collect(Collectors.toMap(Candidate::placeId, Function.identity()));
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 40; i++) {
            String json = mockMvc.perform(post("/api/v1/destinations/random")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody(SEOUL_LAT, SEOUL_LNG, 50, null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tripId").value(matchesPattern("t_[0-9a-f]{6}")))
                    .andReturn().getResponse().getContentAsString();

            String placeId = JsonPath.read(json, "$.destination.placeId");
            Candidate c = byId.get(placeId);
            assertThat(c).as("후보 목록에 있는 장소").isNotNull();
            assertThat((String) JsonPath.read(json, "$.destination.name")).isEqualTo(c.name());
            assertThat((String) JsonPath.read(json, "$.destination.address")).isEqualTo(c.address());
            assertThat((Double) JsonPath.read(json, "$.destination.lat")).isEqualTo(c.lat());
            assertThat((Double) JsonPath.read(json, "$.destination.lng")).isEqualTo(c.lng());
            assertThat((Object) JsonPath.read(json, "$.destination.imageUrl")).isEqualTo(c.imageUrl());

            double distanceKm = ((Number) JsonPath.read(json, "$.destination.distanceKm")).doubleValue();
            assertThat(distanceKm).isBetween(3.0, 50.0);
            assertThat(distanceKm).isCloseTo(c.distanceKmFrom(SEOUL_LAT, SEOUL_LNG), within(0.05));
            seen.add(placeId);
        }
        // 서울역 50km 안에는 후보가 여러 곳이라, 40번 뽑으면 한 곳만 나올 수 없다(추첨이 실제로 랜덤인지).
        assertThat(seen).hasSizeGreaterThan(1);
    }

    @Test
    void demo면_반경과_무관하게_항상_남한산성() throws Exception {
        double expectedKm = Math.round(Geo.distanceKm(SEOUL_LAT, SEOUL_LNG, 37.4786, 127.1817) * 10) / 10.0;
        for (int radiusKm : new int[]{50, 100, 150}) {
            mockMvc.perform(post("/api/v1/destinations/random")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody(SEOUL_LAT, SEOUL_LNG, radiusKm, true)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tripId").value(matchesPattern("t_[0-9a-f]{6}")))
                    .andExpect(jsonPath("$.destination.placeId").value("demo-namhansanseong"))
                    .andExpect(jsonPath("$.destination.name").value("남한산성"))
                    .andExpect(jsonPath("$.destination.distanceKm").value(expectedKm));
        }
    }

    @Test
    void 반경_안에_후보가_없으면_404_NO_CANDIDATE() throws Exception {
        // 동중국해 한가운데(제주 남서쪽 약 350km): 어떤 후보도 150km 안에 없음
        mockMvc.perform(post("/api/v1/destinations/random")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(30.0, 125.0, 150, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_CANDIDATE"))
                .andExpect(jsonPath("$.message").value("반경 150km 안에 추천할 장소가 없어요"));
    }

    @Test
    void 허용되지_않은_radiusKm이면_400() throws Exception {
        mockMvc.perform(post("/api/v1/destinations/random")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(SEOUL_LAT, SEOUL_LNG, 70, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
