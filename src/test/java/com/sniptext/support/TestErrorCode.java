package com.sniptext.support;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 테스트용 컨트롤러가 쓰는 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum TestErrorCode implements ResultCode {

	NAME_REQUIRED(HttpStatus.BAD_REQUEST, "TEST400_1", "이름을 입력해주세요."),
	SHORTCUT_REQUIRED(HttpStatus.BAD_REQUEST, "TEST400_2", "단축어를 입력해주세요."),
	SIZE_RANGE(HttpStatus.BAD_REQUEST, "TEST400_3", "개수는 1 이상이어야 해요."),
	CONFLICT(HttpStatus.CONFLICT, "TEST409_1", "이미 있어요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
