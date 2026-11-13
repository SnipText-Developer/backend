package com.sniptext.domain.ai.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sniptext.domain.ai.entity.ToneStyle;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ValidToneStyleTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	record Request(@ValidToneStyle String style) {
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
	@EnumSource(ToneStyle.class)
	@DisplayName("문체 상수 이름이면 통과한다")
	void valid(ToneStyle style) {
		assertThat(messagesOf(style.name())).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "ESSAY", "boss_external", "Casual", " CASUAL", "CASUAL ", "UNKNOWN"})
	@DisplayName("없거나, 비었거나, 없는 값(삭제한 ESSAY 포함)이거나, 대소문자·공백이 다르면 AI400_1")
	void invalid(String style) {
		assertThat(messagesOf(style)).containsExactly("AI400_1");
	}

	private static List<String> messagesOf(String style) {
		return validator.validate(new Request(style)).stream()
			.map(ConstraintViolation::getMessage)
			.toList();
	}

}
