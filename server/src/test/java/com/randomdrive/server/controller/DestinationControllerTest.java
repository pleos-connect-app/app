package com.randomdrive.server.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** [1] POST /api/v1/destinations/random 테스트. */
@SpringBootTest
@AutoConfigureMockMvc
class DestinationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 랜덤_목적지_정상_응답() throws Exception {
        mockMvc.perform(post("/api/v1/destinations/random")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat": 37.5665, "lng": 126.9780, "radiusKm": 100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(matchesPattern("t_[0-9a-f]{6}")))
                .andExpect(jsonPath("$.destination.placeId").value("126508"))
                .andExpect(jsonPath("$.destination.name").value("안목해변"))
                .andExpect(jsonPath("$.destination.lat").value(37.7718))
                .andExpect(jsonPath("$.destination.lng").value(128.9479))
                .andExpect(jsonPath("$.destination.imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.destination.distanceKm").value(92.4));
    }

    @Test
    void 허용되지_않은_radiusKm이면_400() throws Exception {
        mockMvc.perform(post("/api/v1/destinations/random")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat": 37.5665, "lng": 126.9780, "radiusKm": 70, "demo": false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
