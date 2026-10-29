package com.sniptext.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ContentCipherConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
		.withUserConfiguration(ContentCipherConfig.class);

	@Test
	@DisplayName("본문 키가 없거나 32바이트가 아니거나 Base64가 아니면 기동에 실패한다")
	void startupFailsWithoutValidKey() {
		runner.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.crypto.content-key=")
			.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.crypto.content-key=" + Base64.getEncoder().encodeToString(new byte[31]))
			.run(context -> assertThat(context).hasFailed()
				.getFailure().hasRootCauseInstanceOf(IllegalStateException.class));
		runner.withPropertyValues("sniptext.crypto.content-key=not-base64-key!!")
			.run(context -> assertThat(context).hasFailed()
				.getFailure().hasRootCauseInstanceOf(IllegalStateException.class));
	}

	@Test
	@DisplayName("Base64로 인코딩한 32바이트 키가 있으면 암호화 도구 빈이 하나 생긴다")
	void startsWithValidKey() {
		runner.withPropertyValues("sniptext.crypto.content-key=" + Base64.getEncoder().encodeToString(new byte[32]))
			.run(context -> assertThat(context).hasSingleBean(ContentCipher.class));
	}

}
