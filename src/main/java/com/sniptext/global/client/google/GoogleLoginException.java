package com.sniptext.global.client.google;

/**
 * 구글 쪽 문제로 로그인하지 못함 (구글 장애·연결 실패·시간 초과, 우리 설정 오류). 로그인 API는 AUTH502_1로 응답한다.
 * 사용자가 고칠 수 있는 실패(잘못된·만료·재사용 코드)는 이 예외가 아니라 빈 값이다.
 * 메시지에는 HTTP 상태와 구글 오류 종류만 넣는다. 인가 코드·시크릿·응답 본문을 넣지 않고,
 * HTTP 클라이언트 예외(메시지에 응답 본문이 들어감)를 원인으로 붙이지 않는다.
 */
public class GoogleLoginException extends RuntimeException {

	public GoogleLoginException(String message) {
		super(message);
	}

}
