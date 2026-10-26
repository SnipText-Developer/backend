package com.sniptext.domain.template.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateContentTest {

	@Test
	@DisplayName("CRLF를 LF로 바꾼 값을 갖는다")
	void normalizeLineBreaks() {
		assertThat(TemplateContent.create("안녕하세요\r\n견적입니다\r\n").getValue()).isEqualTo("안녕하세요\n견적입니다\n");
	}

	@Test
	@DisplayName("앞뒤 공백과 줄바꿈, 변수 표기는 그대로 둔다")
	void keepAsIs() {
		String content = "  {{고객명}}님,\n\n견적입니다.  \n";

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
	void lengthAfterNormalize() {
		String content = "a".repeat(2998) + "\r\n" + "a";

		assertThat(content).hasSize(3001);
		assertThat(TemplateContent.create(content).getValue()).hasSize(3000);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "\n\n", "\r\n", " \t\n "})
	@DisplayName("비었거나 공백만 있으면 IllegalArgumentException")
	void blank(String content) {
		assertThatThrownBy(() -> TemplateContent.create(content)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("3001자면 IllegalArgumentException")
	void tooLong() {
		assertThatThrownBy(() -> TemplateContent.create("a".repeat(3001)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("예외 메시지에 입력값이 들어가지 않는다")
	void messageExcludesInput() {
		assertThatThrownBy(() -> TemplateContent.create("비밀본문".repeat(751)))
			.isInstanceOf(IllegalArgumentException.class)
			.message().doesNotContain("비밀본문");
	}

	@Test
	@DisplayName("줄바꿈을 바꾼 뒤 값이 같으면 같고 해시코드도 같다")
	void equality() {
		assertThat(TemplateContent.create("가\r\n나")).isEqualTo(TemplateContent.create("가\n나"))
			.hasSameHashCodeAs(TemplateContent.create("가\n나"));
		assertThat(TemplateContent.create("가")).isNotEqualTo(TemplateContent.create("나"));
	}

}
