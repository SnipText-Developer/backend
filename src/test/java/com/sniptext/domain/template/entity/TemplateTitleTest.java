package com.sniptext.domain.template.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateTitleTest {

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
	@ValueSource(strings = {"   ", "\t", " \n "})
	@DisplayName("비었거나 공백만 있으면 IllegalArgumentException")
	void blank(String title) {
		assertThatThrownBy(() -> TemplateTitle.create(title)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("공백 제거 후 51자면 IllegalArgumentException")
	void tooLong() {
		assertThatThrownBy(() -> TemplateTitle.create("가".repeat(51))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("예외 메시지에 입력값이 들어가지 않는다")
	void messageExcludesInput() {
		assertThatThrownBy(() -> TemplateTitle.create("비밀이름".repeat(13)))
			.isInstanceOf(IllegalArgumentException.class)
			.message().doesNotContain("비밀이름");
	}

	@Test
	@DisplayName("공백 제거 후 값이 같으면 같고 해시코드도 같다")
	void equality() {
		assertThat(TemplateTitle.create(" 견적 ")).isEqualTo(TemplateTitle.create("견적"))
			.hasSameHashCodeAs(TemplateTitle.create("견적"));
		assertThat(TemplateTitle.create("견적")).isNotEqualTo(TemplateTitle.create("안내"));
	}

}
