package com.randomdrive.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 앱 키(X-App-Key)와 IP당 요청 수 제한. */
@SpringBootTest(properties = {
        "api.keys=old-key, new-key",
        "api.rate-limit.enabled=true",
        "api.rate-limit.destinations-per-minute=2",
})
@AutoConfigureMockMvc
class ApiGuardTest {

    private static final String BODY = "{\"lat\": 37.5665, \"lng\": 126.9780, \"radiusKm\": 100}";

    @Autowired
    MockMvc mockMvc;

    private static MockHttpServletRequestBuilder random(String ip) {
        return post("/api/v1/destinations/random").contentType(MediaType.APPLICATION_JSON).content(BODY)
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                });
    }

    @Test
    void 키가_없거나_틀리면_401() throws Exception {
        mockMvc.perform(random("10.0.0.1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists());
        mockMvc.perform(random("10.0.0.1").header(ApiGuardFilter.HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 등록된_키_중_하나면_통과_교체_기간에는_옛_키도_된다() throws Exception {
        mockMvc.perform(random("10.0.0.2").header(ApiGuardFilter.HEADER, "new-key")).andExpect(status().isOk());
        mockMvc.perform(random("10.0.0.2").header(ApiGuardFilter.HEADER, "old-key")).andExpect(status().isOk());
    }

    @Test
    void 공유_페이지는_키_없이_열린다() throws Exception {
        mockMvc.perform(get("/r/{token}", "AAAAAAAAAAAAAAAAAAAAAA")).andExpect(status().isNotFound());
    }

    @Test
    void 한도를_넘으면_429와_Retry_After_다른_IP는_따로_센다() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(random("10.0.0.3").header(ApiGuardFilter.HEADER, "new-key")).andExpect(status().isOk());
        }
        mockMvc.perform(random("10.0.0.3").header(ApiGuardFilter.HEADER, "new-key"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        mockMvc.perform(random("10.0.0.4").header(ApiGuardFilter.HEADER, "new-key")).andExpect(status().isOk());
    }
}
