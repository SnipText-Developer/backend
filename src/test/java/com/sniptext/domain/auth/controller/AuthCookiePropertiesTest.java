package com.sniptext.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class AuthCookiePropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
			ValidationAutoConfiguration.class))
		.withUserConfiguration(AuthCookiePropertiesConfiguration.class);

	@Test
	@DisplayName("SameSite 기본값은 Lax이고 None으로 바꿀 수 있다")
	void sameSite() {
		runner.run(context -> assertThat(context.getBean(AuthCookieProperties.class).sameSite()).isEqualTo("Lax"));
		runner.withPropertyValues("sniptext.auth.cookie.same-site=None")
			.run(context -> assertThat(context.getBean(AuthCookieProperties.class).sameSite()).isEqualTo("None"));
	}

	@Test
	@DisplayName("SameSite가 Strict·Lax·None이 아니면 기동에 실패한다")
	void invalidSameSite() {
		runner.withPropertyValues("sniptext.auth.cookie.same-site=lax")
			.run(context -> assertThat(context).hasFailed());
	}

	@Configuration
	@EnableConfigurationProperties(AuthCookieProperties.class)
	static class AuthCookiePropertiesConfiguration {
	}

}
