package com.randomdrive.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServerApplication.class, args);
	}

	/** 현재 시각. 공유 링크 만료 판단 등에 쓴다(테스트에서 바꿔 끼울 수 있게 빈으로 둔다). */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
