package com.sniptext.global.client.llm;

/**
 * 언어 모델 응답. 결과 문장이 들어 있으므로 로그에 남기지 않는다.
 *
 * @param text         모델이 만든 텍스트
 * @param refused      모델이 답변을 거절했는지 (stop_reason이 refusal)
 * @param outputTokens 출력 토큰 수 (생각 토큰 포함). 응답에 없으면 0
 */
public record LanguageModelResult(String text, boolean refused, int outputTokens) {
}
