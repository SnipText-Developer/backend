package com.sniptext.global.client.llm;

/**
 * 언어 모델 호출. 도메인은 이 인터페이스로만 부른다 (구현은 설정의 provider로 하나만 등록된다).
 * 실패는 {@link LanguageModelTimeoutException}(응답 시간 초과)과 {@link LanguageModelException}(그 밖의 외부 오류)으로 알린다.
 */
public interface LanguageModelClient {

	LanguageModelResult complete(LanguageModelRequest request);

}
