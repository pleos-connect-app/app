package com.randomdrive.server.security;

import com.randomdrive.server.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/**
 * 앱 API(/api/v1/**) 앞단 보호. 공유 페이지(/r/**)는 휴대폰 브라우저가 여는 곳이라 건드리지 않는다.
 * 1. 앱 키: api.keys가 설정돼 있으면 X-App-Key 헤더가 그중 하나와 같아야 한다(아니면 401 UNAUTHORIZED).
 * 2. 요청 수 제한: 클라이언트 IP마다 종류별로 센다(넘으면 429 RATE_LIMITED + Retry-After).
 *    Caddy 뒤에서는 server.forward-headers-strategy=native로 실제 클라이언트 IP를 쓴다.
 *
 * 앱에 들어간 키는 APK에서 꺼낼 수 있으므로 "주소만 아는 아무나"를 막는 수준이다.
 */
@Component
public class ApiGuardFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-App-Key";

    private static final Logger log = LoggerFactory.getLogger(ApiGuardFilter.class);
    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);

    private final ApiGuardProperties props;
    private final RateLimiter limiter;
    private final JsonMapper json = JsonMapper.builder().build();
    private final byte[][] keys;

    public ApiGuardFilter(ApiGuardProperties props, RateLimiter limiter) {
        this.props = props;
        this.limiter = limiter;
        this.keys = props.keys().stream().map(k -> k.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);
        if (keys.length == 0) {
            log.warn("api.keys is empty — /api requests are NOT checked for {}. Set API_KEYS once the app sends it.",
                    HEADER);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (keys.length > 0 && !validKey(request.getHeader(HEADER))) {
            write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "앱 인증 정보가 없거나 올바르지 않아요.");
            return;
        }
        ApiGuardProperties.RateLimit rl = props.rateLimit();
        if (rl.enabled()) {
            String path = request.getRequestURI();
            String kind;
            int limit;
            long window;
            if (path.equals("/api/v1/destinations/random")) {
                kind = "dest"; limit = rl.destinationsPerMinute(); window = MINUTE;
            } else if (path.endsWith("/finish")) {
                kind = "finish"; limit = rl.finishesPerHour(); window = HOUR;
            } else if (path.endsWith("/recap")) {
                kind = "recap"; limit = rl.recapsPerMinute(); window = MINUTE;
            } else if (path.startsWith("/api/v1/map/")) {
                // 추첨 화면 한 번에 위성 타일 수십~백여 장을 받는다
                kind = "tile"; limit = rl.tilesPerMinute(); window = MINUTE;
            } else if (path.equals("/api/v1/geo/area")) {
                kind = "geo"; limit = rl.geosPerMinute(); window = MINUTE;
            } else {
                kind = "other"; limit = rl.othersPerMinute(); window = MINUTE;
            }
            RateLimiter.Decision d = limiter.acquire(request.getRemoteAddr() + "|" + kind, limit, window);
            if (!d.allowed()) {
                response.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
                write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** 길이·내용이 다른 키를 비교 시간으로 구분할 수 없게(상수 시간 비교). */
    private boolean validKey(String given) {
        if (given == null) {
            return false;
        }
        byte[] g = given.getBytes(StandardCharsets.UTF_8);
        boolean ok = false;
        for (byte[] k : keys) {
            ok |= MessageDigest.isEqual(g, k);
        }
        return ok;
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.writeValueAsString(new ErrorResponse(code, message)));
    }
}
