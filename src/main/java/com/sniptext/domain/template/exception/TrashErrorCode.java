package com.sniptext.domain.template.exception;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrashErrorCode implements ResultCode {

	/** 커서를 해석할 수 없거나 size가 1~50 밖 */
	INVALID_PAGE_REQUEST(HttpStatus.BAD_REQUEST, "TRASH400_1", "잘못된 페이지 요청이에요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
