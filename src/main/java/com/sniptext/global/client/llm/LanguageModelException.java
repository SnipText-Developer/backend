package com.sniptext.global.client.llm;

/**
 * 언어 모델 호출 실패 (HTTP 오류, 연결 실패, 쓸 수 없는 응답).
 * 메시지에는 HTTP 상태 코드와 오류 종류만 넣는다. 요청·응답 본문과 키를 넣지 않고,
 * HTTP 클라이언트 예외(메시지에 응답 본문이 들어감)를 원인으로 붙이지 않는다.
 */
public class LanguageModelException extends RuntimeException {

	public LanguageModelException(String message) {
		super(message);
	}

}
