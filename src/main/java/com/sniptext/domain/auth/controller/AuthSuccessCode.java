package com.sniptext.domain.auth.controller;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements ResultCode {

	LOGGED_IN(HttpStatus.OK, "AUTH200_2", "로그인에 성공했습니다.");

	/**
	 * 처음 온 사용자의 로그인 응답 문구 (명세 1번). 코드는 LOGGED_IN과 같은 AUTH200_2다.
	 * 결과 코드 하나에 상수 하나만 둘 수 있어(ResultCodeRegistry가 중복이면 기동을 멈춘다) 문구만 따로 둔다.
	 */
	public static final String SIGN_UP_REQUIRED_MESSAGE = "약관 동의가 필요합니다.";

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
