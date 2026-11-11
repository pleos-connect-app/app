package com.randomdrive.server.tourapi;

/** TourAPI 호출·응답 오류. 메시지에 serviceKey를 넣지 않는다. */
public class TourApiException extends RuntimeException {

    public TourApiException(String message) {
        super(message);
    }
}
