package com.randomdrive.server.dto;

/** 모든 오류 응답의 공통 바디: {"code": "...", "message": "..."}. */
public record ErrorResponse(
        String code,
        String message
) {
}
