package com.sniptext.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class CorsPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
			ValidationAutoConfiguration.class))
		.withUserConfiguration(CorsPropertiesConfiguration.class);

	@Test
	@DisplayName("허용 출처가 없거나 비어 있으면 기동에 실패한다")
	void startupFailsWithoutOrigins() {
		runner.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.cors.allowed-origins=")
			.run(context -> assertThat(context).hasFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = {"*", "https://*.sniptext.kr", "https://sniptext.kr/", "https://sniptext.kr/dashboard",
		"sniptext.kr"})
	@DisplayName("전체 허용(*)이나 경로·끝 슬래시가 붙은 출처는 기동에 실패한다")
	void startupFailsWithInvalidOrigin(String origin) {
		runner.withPropertyValues("sniptext.cors.allowed-origins=https://sniptext.kr," + origin)
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("스킴·호스트·포트로 된 출처는 쉼표로 나뉘고 쉼표 뒤 공백은 지워져 들어간다")
	void startsWithOrigins() {
		runner.withPropertyValues("sniptext.cors.allowed-origins=https://sniptext.kr, http://localhost:3000")
			.run(context -> assertThat(context.getBean(CorsProperties.class).allowedOrigins())
				.containsExactly("https://sniptext.kr", "http://localhost:3000"));
	}

	@Configuration
	@EnableConfigurationProperties(CorsProperties.class)
	static class CorsPropertiesConfiguration {
	}

}
