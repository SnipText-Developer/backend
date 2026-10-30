package com.sniptext.global.client.llm;

/** 언어 모델 응답 시간 초과. 메시지 규칙은 {@link LanguageModelException}과 같다. */
public class LanguageModelTimeoutException extends RuntimeException {

	public LanguageModelTimeoutException(String message) {
		super(message);
	}

}
