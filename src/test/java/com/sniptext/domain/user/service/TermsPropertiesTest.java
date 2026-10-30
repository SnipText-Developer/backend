package com.sniptext.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class TermsPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
			ValidationAutoConfiguration.class))
		.withUserConfiguration(TermsPropertiesConfiguration.class);

	@Test
	@DisplayName("약관 현재 버전이 없거나 공백이거나 20자를 넘으면 기동에 실패한다")
	void startupFailsWithoutValidVersion() {
		runner.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.terms.current-version=")
			.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.terms.current-version=  ")
			.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.terms.current-version=" + "v".repeat(21))
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("약관 현재 버전이 1~20자면 그 값으로 기동한다")
	void startsWithValidVersion() {
		runner.withPropertyValues("sniptext.terms.current-version=v1")
			.run(context -> assertThat(context.getBean(TermsProperties.class).currentVersion()).isEqualTo("v1"));
		runner.withPropertyValues("sniptext.terms.current-version=" + "v".repeat(20))
			.run(context -> assertThat(context.getBean(TermsProperties.class).currentVersion())
				.isEqualTo("v".repeat(20)));
	}

	@Configuration
	@EnableConfigurationProperties(TermsProperties.class)
	static class TermsPropertiesConfiguration {
	}

}
