package com.sniptext.global.exception;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;

/**
 * 도메인 규칙 위반 예외의 부모. 전역 예외 처리가 담긴 결과 코드로 응답한다.
 * 메시지에는 코드만 넣는다 (사용자 입력이 로그에 섞이지 않게).
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ResultCode resultCode;

	public BusinessException(ResultCode resultCode) {
		super(resultCode.getCode());
		this.resultCode = resultCode;
	}

}
