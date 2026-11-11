package com.randomdrive.server.exception;

import org.springframework.http.HttpStatus;

/**
 * 반경 안에 추천할 후보가 하나도 없음 → 404 NO_CANDIDATE.
 * 앱은 이 코드가 오면 message를 사용자에게 그대로 보여 주므로 message는 화면용 한국어 문장이다.
 */
public class NoCandidateException extends ApiException {

    public NoCandidateException(int radiusKm) {
        super(HttpStatus.NOT_FOUND, "NO_CANDIDATE", "반경 " + radiusKm + "km 안에 추천할 장소가 없어요");
    }
}
