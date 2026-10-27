package com.sniptext.global.response;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 명세 공통 규칙의 공통 성공 코드. */
@Getter
@RequiredArgsConstructor
public enum CommonSuccessCode implements ResultCode {

	DEV_TOKEN_ISSUED(HttpStatus.OK, "COMMON200_1", "개발용 토큰을 발급했어요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
