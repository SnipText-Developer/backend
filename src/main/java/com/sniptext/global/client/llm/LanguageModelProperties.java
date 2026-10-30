package com.sniptext.global.client.llm;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 언어 모델 설정 (sniptext.ai). provider는 기본값이 없어 빠뜨리면 기동에 실패한다.
 * toString에 키가 나오지 않게 재정의한다 (설정 바인딩 실패 로그 등).
 *
 * @param provider 쓸 구현. anthropic | fake
 * @param apiKey   anthropic일 때 필수
 * @param model    anthropic일 때 필수
 * @param baseUrl  API 주소
 * @param timeout  응답 대기 시간 (명세: 20초 넘으면 AI504_1)
 */
@ConfigurationProperties("sniptext.ai")
public record LanguageModelProperties(
	Provider provider,
	String apiKey,
	String model,
	@DefaultValue("https://api.anthropic.com") String baseUrl,
	@DefaultValue("20s") Duration timeout
) {

	public enum Provider {
		ANTHROPIC, FAKE
	}

	@Override
	public String toString() {
		return "LanguageModelProperties[provider=%s, model=%s, baseUrl=%s, timeout=%s]".formatted(
			provider, model, baseUrl, timeout);
	}

}
