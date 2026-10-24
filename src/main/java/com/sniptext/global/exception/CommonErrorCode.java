package com.sniptext.global.exception;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 명세 공통 규칙 3장의 공통 에러. */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ResultCode {

	BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400_1", "잘못된 요청입니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401_1", "로그인이 필요해요."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "COMMON401_2", "다시 로그인해주세요."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403_1", "접근 권한이 없어요."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404_1", "요청한 주소를 찾을 수 없어요."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON405_1", "지원하지 않는 요청 방식이에요."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500_1", "서버에 문제가 생겼어요. 잠시 후 다시 시도해주세요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
