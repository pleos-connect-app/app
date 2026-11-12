package com.randomdrive.server.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 고정 창(fixed window) 요청 수 제한. "IP + 종류"마다 창 시작 시각과 횟수만 들고 있는 단순한 방식이다.
 * 서버가 한 대라 메모리에 둔다(재시작하면 초기화돼도 문제없음).
 */
@Component
public class RateLimiter {

    /** 허용이면 0, 거절이면 다시 시도해도 되는 때까지 남은 초. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private record Window(long startMillis, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Decision acquire(String key, int limit, long windowMillis) {
        long now = clock.millis();
        Window w = windows.compute(key, (k, old) ->
                old == null || now - old.startMillis >= windowMillis
                        ? new Window(now, 1)
                        : new Window(old.startMillis, old.count + 1));
        if (w.count <= limit) {
            return new Decision(true, 0);
        }
        long retry = Math.max(1, (w.startMillis + windowMillis - now + 999) / 1000);
        return new Decision(false, retry);
    }

    /** 1시간 넘게 지난 창은 지운다(가장 긴 창이 1시간). */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void evictOld() {
        long cutoff = clock.millis() - TimeUnit.HOURS.toMillis(1);
        windows.values().removeIf(w -> w.startMillis < cutoff);
    }
}
