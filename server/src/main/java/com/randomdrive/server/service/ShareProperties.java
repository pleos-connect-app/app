package com.randomdrive.server.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 공유 링크 설정 (F-08).
 *
 * @param publicBaseUrl 휴대폰에서 열리는 서버 주소(QR에 들어감). 배포 시 환경변수 PUBLIC_BASE_URL로 공개 도메인을 넣는다.
 *                      localhost는 휴대폰 자기 자신을 가리키므로 실제 시연에서는 반드시 바꿔야 한다.
 * @param ttl           공유 링크 유효 기간(리캡 완료 시점부터). 지나면 410 안내 페이지.
 */
@ConfigurationProperties("share")
public record ShareProperties(
        @DefaultValue("http://localhost:8080") String publicBaseUrl,
        @DefaultValue("30d") Duration ttl
) {
    public ShareProperties {
        // 끝의 /는 떼서 "주소 + /r/토큰"을 만들 때 //가 생기지 않게 한다.
        while (publicBaseUrl.endsWith("/")) {
            publicBaseUrl = publicBaseUrl.substring(0, publicBaseUrl.length() - 1);
        }
    }

    public String shareUrl(String token) {
        return publicBaseUrl + "/r/" + token;
    }

    /** 리캡 영상 주소. 공유 링크와 같은 토큰·만료 규칙을 따른다. */
    public String videoUrl(String token) {
        return shareUrl(token) + "/video.mp4";
    }

    /** 리캡 썸네일(16:9) 주소. */
    public String thumbnailUrl(String token) {
        return shareUrl(token) + "/thumbnail.jpg";
    }
}
