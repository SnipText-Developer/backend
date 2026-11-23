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
import org.springframework.context.annotation.Import;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
			ValidationAutoConfiguration.class))
		.withUserConfiguration(CorsTestConfiguration.class);

	@ParameterizedTest
	@ValueSource(strings = {"http://localhost:3000", "https://localhost:3000", "https://127.0.0.1",
		"http://sniptext.kr"})
	@DisplayName("prod 프로필에서 로컬 주소나 http 출처가 하나라도 있으면 기동에 실패한다")
	void prodRejectsLocalOrHttpOrigin(String origin) {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
			.withPropertyValues("sniptext.cors.allowed-origins=https://sniptext.kr," + origin)
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("prod 프로필이어도 로컬이 아닌 https 출처만 있으면 기동한다")
	void prodStartsWithHttpsOrigins() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
			.withPropertyValues("sniptext.cors.allowed-origins=https://sniptext.kr")
			.run(context -> assertThat(context).hasSingleBean(CorsConfigurationSource.class));
	}

	@Test
	@DisplayName("prod 프로필에서 크롬 확장 출처는 https가 아니어도 기동한다")
	void prodAllowsExtensionOrigin() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
			.withPropertyValues(
				"sniptext.cors.allowed-origins=https://sniptext.kr,chrome-extension://abcdefghijklmnopabcdefghijklmnop")
			.run(context -> assertThat(context).hasSingleBean(CorsConfigurationSource.class));
	}

	@Test
	@DisplayName("dev 프로필에서는 로컬 http 출처로도 기동한다 (출시 전 프론트 로컬 연동)")
	void devAllowsLocalOrigin() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
			.withPropertyValues("sniptext.cors.allowed-origins=https://sniptext.kr,http://localhost:3000")
			.run(context -> assertThat(context).hasSingleBean(CorsConfigurationSource.class));
	}

	@Configuration
	@EnableConfigurationProperties(CorsProperties.class)
	@Import(CorsConfig.class)
	static class CorsTestConfiguration {
	}

}
