package com.sniptext.domain.ai.exception;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ResultCode {

	/** 하루 한도 소진. 자정(KST)에 리셋 */
	DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI429_1", "오늘 톤 교정 횟수를 모두 사용했어요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
