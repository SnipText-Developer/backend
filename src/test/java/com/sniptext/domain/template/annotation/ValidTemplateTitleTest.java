package com.sniptext.domain.template.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ValidTemplateTitleTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	record Request(@ValidTemplateTitle String title) {
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
	@ValueSource(strings = {"   ", " "})
	@DisplayName("비었거나 공백만 있으면 TEMPLATE400_1")
	void empty(String title) {
		assertThat(messagesOf(title)).containsExactly("TEMPLATE400_1");
	}

	@Test
	@DisplayName("공백 제거 후 51자면 TEMPLATE400_2")
	void tooLong() {
		assertThat(messagesOf("가".repeat(51))).containsExactly("TEMPLATE400_2");
	}

	@ParameterizedTest
	@ValueSource(strings = {"가", " 견적 안내 "})
	@DisplayName("규칙에 맞으면 위반 없음")
	void valid(String title) {
		assertThat(messagesOf(title)).isEmpty();
		assertThat(messagesOf("  " + "가".repeat(50) + "  ")).isEmpty();
	}

	@Test
	@DisplayName("자모 분리형 50음절은 정규화한 뒤 검사해 통과한다")
	void validInNfd() {
		assertThat(messagesOf(Normalizer.normalize("가".repeat(50), Normalizer.Form.NFD))).isEmpty();
	}

	private static List<String> messagesOf(String title) {
		return validator.validate(new Request(title)).stream()
			.map(ConstraintViolation::getMessage)
			.toList();
	}

}
