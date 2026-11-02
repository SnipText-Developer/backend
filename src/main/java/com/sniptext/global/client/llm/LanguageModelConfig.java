package com.sniptext.global.client.llm;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 언어 모델 구현을 provider(AI_PROVIDER)로 하나만 등록한다. 설정이 틀리면 기동에 실패한다.
 * - provider가 없으면 실패 (기본값이 없어 빠뜨린 서버가 조용히 가짜로 뜨지 않게)
 * - anthropic인데 키(AI_API_KEY)나 모델(AI_MODEL)이 비면 실패
 * - prod 프로필에서 fake면 실패 (운영에서 가짜 교정 결과가 나가지 않게)
 */
@Configuration
@EnableConfigurationProperties(LanguageModelProperties.class)
public class LanguageModelConfig {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	@Bean
	public LanguageModelClient languageModelClient(LanguageModelProperties properties, Environment environment) {
		if (properties.provider() == null) {
			throw new IllegalStateException("언어 모델 provider(AI_PROVIDER)를 anthropic 또는 fake로 지정해야 합니다");
		}
		return switch (properties.provider()) {
			case FAKE -> {
				if (environment.matchesProfiles("prod")) {
					throw new IllegalStateException("운영 프로필에서는 가짜 언어 모델을 쓸 수 없습니다");
				}
				yield new FakeLanguageModelClient();
			}
			case ANTHROPIC -> {
				requireText(properties.apiKey(), "AI_API_KEY");
				requireText(properties.model(), "AI_MODEL");
				yield new AnthropicLanguageModelClient(
					RestClient.builder().requestFactory(requestFactory(properties.timeout())), properties);
			}
		};
	}

	/** 연결 제한 5초, 응답 제한은 설정값. */
	static JdkClientHttpRequestFactory requestFactory(Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return requestFactory;
	}

	private static void requireText(String value, String name) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalStateException("언어 모델 provider가 anthropic이면 %s가 필요합니다".formatted(name));
		}
	}

}
