package com.sniptext.domain.template.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateContentTest {

	/** 맥에서 입력하면 들어오는 자모 분리형(NFD) 문자열 */
	private static String nfd(String text) {
		String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
		assertThat(decomposed).isNotEqualTo(text);
		return decomposed;
	}

	@Test
	@DisplayName("CRLF를 LF로 바꾼 값을 갖는다")
	void normalizeLineBreaks() {
		assertThat(TemplateContent.create("안녕하세요\r\n견적입니다\r\n").getValue()).isEqualTo("안녕하세요\n견적입니다\n");
	}

	@Test
	@DisplayName("자모 분리형은 NFC로 정규화한 값을 갖는다")
	void normalizeNfc() {
		assertThat(TemplateContent.create(nfd("{{고객명}}님, 견적입니다.")).getValue())
			.isEqualTo("{{고객명}}님, 견적입니다.");
	}

	@Test
	@DisplayName("앞뒤 공백과 줄바꿈, 변수 표기는 그대로 둔다")
	void keepAsIs() {
		String content = "  {{고객명}}님,\n\n견적입니다.  \n";

		assertThat(TemplateContent.create(content).getValue()).isEqualTo(content);
	}

	@Test
	@DisplayName("1자와 3000자는 만들 수 있다")
	void boundaryLengths() {
		assertThat(TemplateContent.create("a").getValue()).hasSize(1);
		assertThat(TemplateContent.create("a".repeat(3000)).getValue()).hasSize(3000);
	}

	@Test
	@DisplayName("CRLF를 LF로 바꾼 뒤 글자 수를 센다 (바꾸기 전 3001자도 바꾼 뒤 3000자면 통과)")
	void lengthAfterLineBreaks() {
		String content = "a".repeat(2998) + "\r\n" + "a";

		assertThat(content).hasSize(3001);
		assertThat(TemplateContent.findViolation(content)).isEmpty();
		assertThat(TemplateContent.create(content).getValue()).hasSize(3000);
	}

	@Test
	@DisplayName("NFC로 정규화한 뒤 글자 수를 센다 (자모 분리형 3000음절은 통과)")
	void lengthAfterNfc() {
		String content = nfd("가".repeat(3000));

		assertThat(content).hasSizeGreaterThan(3000);
		assertThat(TemplateContent.findViolation(content)).isEmpty();
		assertThat(TemplateContent.create(content).getValue()).hasSize(3000);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\n\n", "\r\n", " \t\n ", " \n", "﻿", "　 "})
	@DisplayName("비었거나 공백만 있으면 EMPTY (화면이 공백으로 보는 NBSP·BOM 포함)")
	void empty(String content) {
		assertThat(TemplateContent.findViolation(content)).contains(TemplateContentViolation.EMPTY);
	}

	@Test
	@DisplayName("3001자면 TOO_LONG")
	void tooLong() {
		assertThat(TemplateContent.findViolation("a".repeat(3001))).contains(TemplateContentViolation.TOO_LONG);
	}

	@Test
	@DisplayName("규칙에 맞으면 위반 없음")
	void noViolation() {
		assertThat(TemplateContent.findViolation("안녕하세요")).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", " "})
	@DisplayName("비었거나 공백만 있으면 create는 IllegalArgumentException")
	void createBlank(String content) {
		assertThatThrownBy(() -> TemplateContent.create(content)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("3001자면 create는 IllegalArgumentException")
	void createTooLong() {
		assertThatThrownBy(() -> TemplateContent.create("a".repeat(3001)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("예외 메시지에는 위반 이름만 있고 입력값이 들어가지 않는다")
	void messageExcludesInput() {
		assertThatThrownBy(() -> TemplateContent.create("비밀본문".repeat(751)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("TOO_LONG")
			.message().doesNotContain("비밀본문");
	}

	@Test
	@DisplayName("정리한 뒤 값이 같으면 같고 해시코드도 같다")
	void equality() {
		assertThat(TemplateContent.create("가\r\n나")).isEqualTo(TemplateContent.create("가\n나"))
			.hasSameHashCodeAs(TemplateContent.create("가\n나"));
		assertThat(TemplateContent.create(nfd("견적"))).isEqualTo(TemplateContent.create("견적"));
		assertThat(TemplateContent.create("가")).isNotEqualTo(TemplateContent.create("나"));
	}

}
