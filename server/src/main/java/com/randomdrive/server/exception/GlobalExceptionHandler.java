package com.randomdrive.server.exception;

import com.randomdrive.server.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 모든 컨트롤러에서 던져진 예외를 한곳에서 잡아 {"code","message"} 형식으로 바꾼다.
 * 컨트롤러마다 try/catch를 쓰지 않아도 되는 이유가 이 클래스.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_REQUEST = "INVALID_REQUEST";

    /** 우리가 직접 던진 예외(예: TripNotFoundException). */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    /** @Valid 검증 실패(필수값 누락, radiusKm 허용값 아님, points 개수 범위 밖 등). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(err -> err instanceof FieldError fe
                        ? fe.getField() + ": " + fe.getDefaultMessage()
                        : err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return badRequest(message);
    }

    /** JSON 문법 오류, 타입 불일치, 알 수 없는 enum 값(events.type) 등 바디를 읽을 수 없는 경우. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return badRequest("malformed request body");
    }

    /** 쿼리·경로 값 누락이나 숫자가 아닌 값(예: /geo/area?lat=abc). */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadParam(Exception e) {
        return badRequest("invalid request parameter");
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(INVALID_REQUEST, message));
    }
}
