package com.randomdrive.server.exception;

import org.springframework.http.HttpStatus;

/**
 * 서비스 코드에서 "이 HTTP 상태 + 이 code로 응답해라"를 표현하는 예외의 공통 부모.
 * GlobalExceptionHandler가 잡아서 ErrorResponse로 바꿔준다.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
