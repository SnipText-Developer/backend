package com.sniptext.global.client.google;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class GooglePropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
			ValidationAutoConfiguration.class))
		.withUserConfiguration(GooglePropertiesConfiguration.class);

	@Test
	@DisplayName("구글 클라이언트 ID가 없거나 공백이면 기동에 실패한다")
	void startupFailsWithoutClientId() {
		runner.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.google.client-id=")
			.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.google.client-id=  ")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("구글 클라이언트 ID가 있으면 그 값으로 기동한다")
	void startsWithClientId() {
		runner.withPropertyValues("sniptext.google.client-id=sniptext-web.apps.googleusercontent.com")
			.run(context -> assertThat(context.getBean(GoogleProperties.class).clientId())
				.isEqualTo("sniptext-web.apps.googleusercontent.com"));
	}

	@Configuration
	@EnableConfigurationProperties(GoogleProperties.class)
	static class GooglePropertiesConfiguration {
	}

}
