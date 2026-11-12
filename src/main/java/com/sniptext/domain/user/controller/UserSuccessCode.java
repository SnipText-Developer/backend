package com.sniptext.domain.user.controller;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements ResultCode {

	USER_FOUND(HttpStatus.OK, "USER200_1", "내 정보 조회에 성공했습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
