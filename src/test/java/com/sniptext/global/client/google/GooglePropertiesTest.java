package com.sniptext.global.client.google;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class GooglePropertiesTest {

	private static final String CLIENT_ID = "sniptext.google.client-id=sniptext-web.apps.googleusercontent.com";
	private static final String CLIENT_SECRET = "sniptext.google.client-secret=secret-value-1234";
	private static final String REDIRECT_URIS =
		"sniptext.google.redirect-uris=https://sniptext.io/auth/google/callback,http://localhost:5173/auth/google/callback";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
			ValidationAutoConfiguration.class))
		.withUserConfiguration(GooglePropertiesConfiguration.class);

	@Test
	@DisplayName("구글 클라이언트 ID가 없거나 공백이면 기동에 실패한다")
	void startupFailsWithoutClientId() {
		runner.withPropertyValues(CLIENT_SECRET, REDIRECT_URIS)
			.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.google.client-id=  ", CLIENT_SECRET, REDIRECT_URIS)
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("구글 클라이언트 시크릿이 없거나 공백이면 기동에 실패한다")
	void startupFailsWithoutClientSecret() {
		runner.withPropertyValues(CLIENT_ID, REDIRECT_URIS)
			.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues(CLIENT_ID, "sniptext.google.client-secret=  ", REDIRECT_URIS)
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("redirectUri 허용 목록이 없거나 비어 있으면 기동에 실패한다")
	void startupFailsWithoutRedirectUris() {
		runner.withPropertyValues(CLIENT_ID, CLIENT_SECRET)
			.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues(CLIENT_ID, CLIENT_SECRET, "sniptext.google.redirect-uris=")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("값이 모두 있으면 기동하고, 허용 목록은 쉼표로 나뉘며 토큰 주소·시간 제한은 기본값이다")
	void startsWithValues() {
		runner.withPropertyValues(CLIENT_ID, CLIENT_SECRET, REDIRECT_URIS).run(context -> {
			GoogleProperties properties = context.getBean(GoogleProperties.class);
			assertThat(properties.redirectUris()).containsExactly(
				"https://sniptext.io/auth/google/callback", "http://localhost:5173/auth/google/callback");
			assertThat(properties.tokenUri()).isEqualTo("https://oauth2.googleapis.com/token");
			assertThat(properties.jwkSetUri()).isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
			assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
			assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
		});
	}

	@Test
	@DisplayName("toString에 시크릿이 나오지 않는다")
	void toStringHidesSecret() {
		GoogleProperties properties = new GoogleProperties("client-id", "secret-value-1234",
			List.of("https://sniptext.io/auth/google/callback"), "https://oauth2.googleapis.com/token",
			"https://www.googleapis.com/oauth2/v3/certs", Duration.ofSeconds(3), Duration.ofSeconds(5));

		assertThat(properties.toString()).doesNotContain("secret-value-1234").contains("client-id");
	}

	@Configuration
	@EnableConfigurationProperties(GoogleProperties.class)
	static class GooglePropertiesConfiguration {
	}

}
