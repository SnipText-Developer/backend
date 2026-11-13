package com.sniptext.domain.ai.annotation;

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

class ValidToneTextTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	record Request(@ValidToneText String text) {
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
	@ValueSource(strings = {"   ", "\r\n\t", " ", "﻿", "　", "  ﻿　\n"})
	@DisplayName("없거나 비었거나 공백만(NBSP·BOM·전각 공백 포함) 있으면 AI400_2")
	void empty(String text) {
		assertThat(messagesOf(text)).containsExactly("AI400_2");
	}

	@Test
	@DisplayName("3000자는 통과하고 3001자는 AI400_3")
	void lengthBoundary() {
		assertThat(messagesOf("가".repeat(3000))).isEmpty();
		assertThat(messagesOf("가".repeat(3001))).containsExactly("AI400_3");
	}

	@Test
	@DisplayName("정규화하지 않고 받은 그대로 센다 (자모 분리형 1500음절은 3000자라 통과, 1501음절은 AI400_3)")
	void countsAsReceived() {
		assertThat(messagesOf(Normalizer.normalize("가".repeat(1500), Normalizer.Form.NFD))).isEmpty();
		assertThat(messagesOf(Normalizer.normalize("가".repeat(1501), Normalizer.Form.NFD))).containsExactly("AI400_3");
	}

	@Test
	@DisplayName("앞뒤 공백이 있어도 내용이 있으면 통과한다")
	void valid() {
		assertThat(messagesOf("  이번 달은 외주 못 받는다고 해줘  ")).isEmpty();
	}

	private static List<String> messagesOf(String text) {
		return validator.validate(new Request(text)).stream()
			.map(ConstraintViolation::getMessage)
			.toList();
	}

}
