package com.sniptext.global.response;

import org.springframework.http.HttpStatus;

/**
 * 응답의 결과 코드. 도메인별 성공·에러 코드 열거형이 구현한다.
 * 코드 형식은 {도메인}{HTTP 상태}_{번호}, 문구는 명세 그대로 쓴다.
 */
public interface ResultCode {

	HttpStatus getHttpStatus();

	String getCode();

	String getMessage();

}
