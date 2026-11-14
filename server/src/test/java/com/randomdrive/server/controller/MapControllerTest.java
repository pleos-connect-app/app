package com.randomdrive.server.controller;

import com.randomdrive.server.recap.VWorldClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 앱 지도 중계: VWorld 키 없이 앱이 위성 타일·지역 이름을 받는다. */
@SpringBootTest
@AutoConfigureMockMvc
class MapControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    VWorldClient vworld;

    @Test
    void 위성_타일을_JPEG로_중계하고_같은_타일은_다시_받지_않는다() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
        when(vworld.satelliteTileBytes(12, 3493, 1586)).thenReturn(jpeg);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/map/satellite/12/3493/1586.jpeg"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                    .andExpect(header().string("Cache-Control", containsString("private")))
                    .andExpect(content().bytes(jpeg));
        }
        verify(vworld, times(1)).satelliteTileBytes(12, 3493, 1586);
    }

    @Test
    void 타일을_받지_못하면_503_MAP_UNAVAILABLE() throws Exception {
        when(vworld.satelliteTileBytes(anyInt(), anyInt(), anyInt())).thenReturn(null);
        mockMvc.perform(get("/api/v1/map/satellite/10/873/396.jpeg"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MAP_UNAVAILABLE"));
    }

    @Test
    void 범위_밖_타일은_400() throws Exception {
        mockMvc.perform(get("/api/v1/map/satellite/19/1/1.jpeg")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/map/satellite/6/64/1.jpeg")).andExpect(status().isBadRequest());
    }

    @Test
    void 지역_이름() throws Exception {
        when(vworld.area(37.4527, 126.9037)).thenReturn(new VWorldClient.Area("서울특별시 금천구 시흥동", "금천구 시흥동"));
        mockMvc.perform(get("/api/v1/geo/area").param("lat", "37.4527").param("lng", "126.9037"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full").value("서울특별시 금천구 시흥동"))
                .andExpect(jsonPath("$.short").value("금천구 시흥동"));
    }

    @Test
    void 지역을_찾지_못하면_둘_다_null() throws Exception {
        when(vworld.area(anyDouble(), anyDouble())).thenReturn(null);
        mockMvc.perform(get("/api/v1/geo/area").param("lat", "37.0").param("lng", "125.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full").doesNotExist())
                .andExpect(jsonPath("$.short").doesNotExist());
    }

    @Test
    void 장소_이름이나_주소로_좌표를_찾는다() throws Exception {
        when(vworld.search("서울특별시 양천구 목동중앙남로 11-15 (목동)")).thenReturn(new double[]{37.5262, 126.8752});
        mockMvc.perform(get("/api/v1/geo/search").param("query", "서울특별시 양천구 목동중앙남로 11-15 (목동)"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(37.5262))
                .andExpect(jsonPath("$.lng").value(126.8752));
        when(vworld.search("없는 곳")).thenReturn(null);
        mockMvc.perform(get("/api/v1/geo/search").param("query", "없는 곳"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").doesNotExist());
        mockMvc.perform(get("/api/v1/geo/search").param("query", " ")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/geo/search")).andExpect(status().isBadRequest());
    }

    @Test
    void 좌표가_없거나_잘못되면_400() throws Exception {
        mockMvc.perform(get("/api/v1/geo/area").param("lat", "37.5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/geo/area").param("lat", "abc").param("lng", "127")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/geo/area").param("lat", "91").param("lng", "127")).andExpect(status().isBadRequest());
    }
}
