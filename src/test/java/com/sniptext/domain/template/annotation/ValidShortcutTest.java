package com.sniptext.domain.template.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ValidShortcutTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	record Request(@ValidShortcut String shortcut) {
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
	@ValueSource(strings = "/")
	@DisplayName("비었거나 \"/\"만 있으면 TEMPLATE400_3")
	void empty(String shortcut) {
		assertThat(messagesOf(shortcut)).containsExactly("TEMPLATE400_3");
	}

	@ParameterizedTest
	@CsvSource({
		"/a,      TEMPLATE400_4",
		"/견적!,   TEMPLATE400_4",
		"'/견 적', TEMPLATE400_4",
		"/톤교정,   TEMPLATE400_5"
	})
	@DisplayName("형식 불일치는 TEMPLATE400_4, 예약어는 TEMPLATE400_5 하나만 남긴다")
	void violation(String shortcut, String code) {
		assertThat(messagesOf(shortcut)).containsExactly(code);
	}

	@ParameterizedTest
	@ValueSource(strings = {"/견적", "/Quote", "/톤교정2"})
	@DisplayName("규칙에 맞으면 위반 없음")
	void valid(String shortcut) {
		assertThat(messagesOf(shortcut)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = "/견적요청서작성안내문")
	@DisplayName("자모 분리형으로 입력해도 정규화한 뒤 검사해 통과한다")
	void validInNfd(String shortcut) {
		assertThat(messagesOf(Normalizer.normalize(shortcut, Normalizer.Form.NFD))).isEmpty();
	}

	private static List<String> messagesOf(String shortcut) {
		return validator.validate(new Request(shortcut)).stream()
			.map(ConstraintViolation::getMessage)
			.toList();
	}

}
