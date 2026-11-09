package com.sniptext.domain.template.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ValidUsageChannelTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	record Request(@ValidUsageChannel String channel) {
	}

	@BeforeAll
	static void setUp() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		validatorFactory.close();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "dashboard_copy", "1", "SLACK_COPY"})
	@DisplayName("없거나 지원하지 않는 채널이면 TEMPLATE400_9 하나만 남긴다")
	void unsupported(String channel) {
		assertThat(messagesOf(channel)).containsExactly("TEMPLATE400_9");
	}

	@ParameterizedTest
	@ValueSource(strings = {"DASHBOARD_COPY", "POPUP_COPY", "EMAIL_INLINE"})
	@DisplayName("명세의 채널이면 위반 없음")
	void supported(String channel) {
		assertThat(messagesOf(channel)).isEmpty();
	}

	private static List<String> messagesOf(String channel) {
		return validator.validate(new Request(channel)).stream()
			.map(ConstraintViolation::getMessage)
			.toList();
	}

}
