package com.randomdrive.server.recap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * 리캡 영상·썸네일 생성 설정 (F-07).
 *
 * @param storageDir     영상·썸네일 파일을 저장할 폴더. 서버 작업 폴더 기준 상대 경로도 된다.
 * @param video          false면 영상은 만들지 않고 썸네일만 만든다.
 * @param thumbnail      false면 썸네일을 만들지 않는다.
 * @param ffmpegPath     ffmpeg 실행 파일. PATH에 있으면 "ffmpeg"로 충분. 찾을 수 없으면 영상 없이 진행한다.
 * @param downloadPhotos false면 목적지 사진을 받지 않는다(테스트·오프라인용).
 * @param workers        동시에 만드는 리캡 수. 0이면 별도 스레드 없이 finish 요청 안에서 바로 만든다(테스트용).
 * @param fontFile       한글 글꼴 파일(.ttf/.otf) 경로. 비우면 설치된 한글 글꼴을 찾아 쓴다.
 */
@ConfigurationProperties("recap")
public record RecapProperties(
        @DefaultValue("data/recaps") Path storageDir,
        @DefaultValue("true") boolean video,
        @DefaultValue("true") boolean thumbnail,
        @DefaultValue("ffmpeg") String ffmpegPath,
        @DefaultValue("true") boolean downloadPhotos,
        @DefaultValue("1") int workers,
        @DefaultValue("") String fontFile
) {
}
