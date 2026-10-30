package com.sniptext.global.client.llm;

/**
 * 언어 모델 요청. 원문이 들어 있으므로 로그에 남기지 않는다 (record의 toString에도 원문이 나온다).
 *
 * @param systemPrompt    시스템 지시
 * @param userText        사용자 메시지
 * @param maxOutputTokens 출력 토큰 상한 (비용 상한)
 */
public record LanguageModelRequest(String systemPrompt, String userText, int maxOutputTokens) {
}
