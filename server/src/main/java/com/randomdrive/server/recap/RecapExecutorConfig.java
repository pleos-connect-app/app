package com.randomdrive.server.recap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 리캡 생성 작업을 돌리는 스레드. 영상 인코딩은 CPU를 많이 쓰므로 동시에 recap.workers개까지만 만든다.
 * workers=0이면 별도 스레드 없이 finish 요청 안에서 바로 만든다(테스트에서 결과를 바로 확인하려고).
 */
@Configuration
public class RecapExecutorConfig {

    /** 리캡 생성 작업 실행기. */
    @FunctionalInterface
    public interface RecapExecutor {
        void submit(Runnable job);
    }

    @Bean(destroyMethod = "")
    RecapExecutor recapExecutor(RecapProperties props) {
        if (props.workers() <= 0) {
            return Runnable::run;
        }
        AtomicInteger n = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(props.workers(), r -> {
            Thread t = new Thread(r, "recap-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        return pool::execute;
    }
}
