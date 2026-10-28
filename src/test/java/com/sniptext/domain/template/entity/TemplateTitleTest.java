package com.sniptext.domain.template.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateTitleTest {

	/** 맥에서 입력하면 들어오는 자모 분리형(NFD) 문자열 */
	private static String nfd(String text) {
		String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
		assertThat(decomposed).isNotEqualTo(text);
		return decomposed;
	}

	@Test
	@DisplayName("앞뒤 공백을 제거한 값을 갖는다")
	void strip() {
		assertThat(TemplateTitle.create("  견적 안내\t").getValue()).isEqualTo("견적 안내");
	}

	@Test
	@DisplayName("가운데 공백은 그대로 둔다")
	void keepInnerSpaces() {
		assertThat(TemplateTitle.create("견적  안내").getValue()).isEqualTo("견적  안내");
	}

	@ParameterizedTest
	@ValueSource(strings = {"견적\n안내", "견적\t안내", "견적\r\n안내", "견적\n\n\t안내", "견적\u2028안내"})
	@DisplayName("가운데 줄바꿈·탭은 이어진 묶음째 공백 하나로 바꾼다 (한 줄로 보여 주는 곳에서 깨지지 않게)")
	void replaceInnerLineBreaksAndTabs(String title) {
		assertThat(TemplateTitle.create(title).getValue()).isEqualTo("견적 안내");
	}

	@Test
	@DisplayName("줄바꿈·탭을 공백으로 바꾼 뒤 글자 수를 센다 (CRLF가 공백 하나가 되어 50자면 통과)")
	void lengthAfterReplacingLineBreaks() {
		String title = "가".repeat(25) + "\r\n" + "가".repeat(24);

		assertThat(title).hasSize(51);
		assertThat(TemplateTitle.findViolation(title)).isEmpty();
		assertThat(TemplateTitle.create(title).getValue()).hasSize(50);
	}

	@ParameterizedTest
	@ValueSource(strings = {" 견적 ", "﻿견적", "　견적　", "견적 ", " 견적 "})
	@DisplayName("화면(JS trim)이 공백으로 보는 NBSP·BOM·전각 공백·줄 구분자도 앞뒤에서 제거한다")
	void stripUnicodeSpaces(String title) {
		assertThat(TemplateTitle.create(title).getValue()).isEqualTo("견적");
	}

	@Test
	@DisplayName("자모 분리형은 NFC로 정규화한 값을 갖는다")
	void normalizeNfc() {
		assertThat(TemplateTitle.create(nfd("견적 안내")).getValue()).isEqualTo("견적 안내");
	}

	@Test
	@DisplayName("NFC로 정규화한 뒤 글자 수를 센다 (자모 분리형 50음절은 통과)")
	void lengthAfterNfc() {
		String title = nfd("가".repeat(50));

		assertThat(title).hasSizeGreaterThan(50);
		assertThat(TemplateTitle.findViolation(title)).isEmpty();
		assertThat(TemplateTitle.create(title).getValue()).hasSize(50);
	}

	@Test
	@DisplayName("공백 제거 후 1자와 50자는 만들 수 있다")
	void boundaryLengths() {
		assertThat(TemplateTitle.create("가").getValue()).hasSize(1);
		assertThat(TemplateTitle.create("가".repeat(50)).getValue()).hasSize(50);
	}

	@Test
	@DisplayName("앞뒤 공백 때문에 50자를 넘어도 제거 후 50자면 만들 수 있다")
	void lengthAfterStrip() {
		assertThat(TemplateTitle.create("  " + "가".repeat(50) + "  ").getValue()).hasSize(50);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\t", " \n ", " ", "﻿", "  　"})
	@DisplayName("비었거나 공백만 있으면 EMPTY")
	void empty(String title) {
		assertThat(TemplateTitle.findViolation(title)).contains(TemplateTitleViolation.EMPTY);
	}

	@Test
	@DisplayName("공백 제거 후 51자면 TOO_LONG")
	void tooLong() {
		assertThat(TemplateTitle.findViolation("가".repeat(51))).contains(TemplateTitleViolation.TOO_LONG);
	}

	@Test
	@DisplayName("규칙에 맞으면 위반 없음")
	void noViolation() {
		assertThat(TemplateTitle.findViolation(" 견적 안내 ")).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", " "})
	@DisplayName("비었거나 공백만 있으면 create는 IllegalArgumentException")
	void createBlank(String title) {
		assertThatThrownBy(() -> TemplateTitle.create(title)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("공백 제거 후 51자면 create는 IllegalArgumentException")
	void createTooLong() {
		assertThatThrownBy(() -> TemplateTitle.create("가".repeat(51))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("예외 메시지에는 위반 이름만 있고 입력값이 들어가지 않는다")
	void messageExcludesInput() {
		assertThatThrownBy(() -> TemplateTitle.create("비밀이름".repeat(13)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("TOO_LONG")
			.message().doesNotContain("비밀이름");
	}

	@Test
	@DisplayName("정리한 뒤 값이 같으면 같고 해시코드도 같다")
	void equality() {
		assertThat(TemplateTitle.create(" 견적 ")).isEqualTo(TemplateTitle.create("견적"))
			.hasSameHashCodeAs(TemplateTitle.create("견적"));
		assertThat(TemplateTitle.create(nfd("견적"))).isEqualTo(TemplateTitle.create("견적"));
		assertThat(TemplateTitle.create("견적")).isNotEqualTo(TemplateTitle.create("안내"));
	}

}
