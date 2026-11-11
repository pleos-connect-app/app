package com.randomdrive.server.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 한국관광공사 TourAPI(공공데이터포털 "국문 관광정보 서비스_GW") 설정.
 * serviceKey는 비밀값이라 application-local.yml 또는 환경변수 TOUR_API_KEY로만 넣는다.
 *
 * @param serviceKey 공공데이터포털 인증키(Decoding 키). 클라이언트가 URL 인코딩한다.
 * @param baseUrl    KorService2 기본 주소
 * @param output     importCandidates가 후보 파일을 쓸 경로(server/ 기준)
 */
@ConfigurationProperties("tour-api")
public record TourApiProperties(
        String serviceKey,
        @DefaultValue("https://apis.data.go.kr/B551011/KorService2") String baseUrl,
        @DefaultValue("src/main/resources/candidates.json") String output
) {
}
