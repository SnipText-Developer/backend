package com.sniptext.global.client.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** DB 없이 설정 클래스만 띄워 provider별 등록 결과와 기동 실패를 확인한다. */
class LanguageModelConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
		.withUserConfiguration(LanguageModelConfig.class);

	@Test
	@DisplayName("provider=fake면 가짜 구현 빈 하나만 생긴다")
	void fake() {
		runner.withPropertyValues("sniptext.ai.provider=fake")
			.run(context -> assertThat(context).hasSingleBean(LanguageModelClient.class)
				.getBean(LanguageModelClient.class).isInstanceOf(FakeLanguageModelClient.class));
	}

	@Test
	@DisplayName("provider=anthropic이고 키·모델이 있으면 Anthropic 구현 빈 하나만 생긴다")
	void anthropic() {
		runner.withPropertyValues("sniptext.ai.provider=anthropic", "sniptext.ai.api-key=test-key",
				"sniptext.ai.model=test-model")
			.run(context -> assertThat(context).hasSingleBean(LanguageModelClient.class)
				.getBean(LanguageModelClient.class).isInstanceOf(AnthropicLanguageModelClient.class));
	}

	@Test
	@DisplayName("provider=anthropic인데 키나 모델이 비면 기동에 실패한다")
	void anthropicWithoutKeyOrModel() {
		runner.withPropertyValues("sniptext.ai.provider=anthropic", "sniptext.ai.model=test-model")
			.run(context -> assertThat(context).hasFailed()
				.getFailure().hasRootCauseInstanceOf(IllegalStateException.class)
				.rootCause().hasMessageContaining("AI_API_KEY"));
		runner.withPropertyValues("sniptext.ai.provider=anthropic", "sniptext.ai.api-key= ", "sniptext.ai.model=test-model")
			.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.ai.provider=anthropic", "sniptext.ai.api-key=test-key")
			.run(context -> assertThat(context).hasFailed()
				.getFailure().hasRootCauseInstanceOf(IllegalStateException.class)
				.rootCause().hasMessageContaining("AI_MODEL"));
	}

	@Test
	@DisplayName("provider가 없거나 모르는 값이면 기동에 실패한다")
	void withoutProvider() {
		runner.run(context -> assertThat(context).hasFailed()
			.getFailure().hasRootCauseInstanceOf(IllegalStateException.class)
			.rootCause().hasMessageContaining("AI_PROVIDER"));
		runner.withPropertyValues("sniptext.ai.provider=openai")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("prod 프로필에서 provider=fake면 기동에 실패한다")
	void fakeInProd() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
			.withPropertyValues("sniptext.ai.provider=fake")
			.run(context -> assertThat(context).hasFailed()
				.getFailure().hasRootCauseInstanceOf(IllegalStateException.class));
	}

	@Test
	@DisplayName("prod 프로필이어도 provider=anthropic이면 기동한다")
	void anthropicInProd() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
			.withPropertyValues("sniptext.ai.provider=anthropic", "sniptext.ai.api-key=test-key",
				"sniptext.ai.model=test-model")
			.run(context -> assertThat(context).hasSingleBean(LanguageModelClient.class)
				.getBean(LanguageModelClient.class).isInstanceOf(AnthropicLanguageModelClient.class));
	}

}
