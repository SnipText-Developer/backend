package com.sniptext.domain.ai.controller;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiSuccessCode implements ResultCode {

	TONE_CORRECTED(HttpStatus.OK, "AI200_1", "톤 교정에 성공했습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
