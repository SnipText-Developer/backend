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

class ValidTemplateContentTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	record Request(@ValidTemplateContent String content) {
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
	@ValueSource(strings = {"   ", "\r\n", " \n"})
	@DisplayName("비었거나 공백만 있으면 TEMPLATE400_6")
	void empty(String content) {
		assertThat(messagesOf(content)).containsExactly("TEMPLATE400_6");
	}

	@Test
	@DisplayName("3001자면 TEMPLATE400_7")
	void tooLong() {
		assertThat(messagesOf("a".repeat(3001))).containsExactly("TEMPLATE400_7");
	}

	@Test
	@DisplayName("CRLF를 LF로 바꾼 뒤 3000자면 통과한다")
	void validAfterLineBreaks() {
		assertThat(messagesOf("a".repeat(2998) + "\r\n" + "a")).isEmpty();
	}

	@Test
	@DisplayName("자모 분리형 3000음절은 정규화한 뒤 검사해 통과한다")
	void validInNfd() {
		assertThat(messagesOf(Normalizer.normalize("가".repeat(3000), Normalizer.Form.NFD))).isEmpty();
	}

	@Test
	@DisplayName("앞뒤 공백이 있는 본문도 규칙에 맞으면 위반 없음")
	void valid() {
		assertThat(messagesOf("  {{고객명}}님,\n견적입니다.  ")).isEmpty();
	}

	private static List<String> messagesOf(String content) {
		return validator.validate(new Request(content)).stream()
			.map(ConstraintViolation::getMessage)
			.toList();
	}

}
