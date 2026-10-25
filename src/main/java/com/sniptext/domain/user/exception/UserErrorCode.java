package com.sniptext.domain.user.exception;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ResultCode {

	/** 없는 회원 또는 탈퇴 회원 */
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404_1", "회원 정보를 찾을 수 없어요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
