package com.sniptext.support;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 테스트용 컨트롤러가 쓰는 성공 코드. */
@Getter
@RequiredArgsConstructor
public enum TestSuccessCode implements ResultCode {

	OK(HttpStatus.OK, "TEST200_1", "성공했어요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
