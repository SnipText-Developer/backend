package com.sniptext.global.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextWhitespaceTest {

	@ParameterizedTest
	@ValueSource(strings = {"", " ", " ", "﻿", "　", "\n", "\r\n", "\t", "  ﻿　\n\t"})
	@DisplayName("NBSP·BOM·전각 공백·줄바꿈·탭만 있으면 비었다고 본다")
	void blank(String text) {
		assertThat(TextWhitespace.isBlank(text)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"a", " 가 ", " .﻿", "\t0\n"})
	@DisplayName("일반 글자가 하나라도 있으면 비었다고 보지 않는다")
	void notBlank(String text) {
		assertThat(TextWhitespace.isBlank(text)).isFalse();
	}

	@Test
	@DisplayName("strip은 양끝의 NBSP·BOM·전각 공백·줄바꿈·탭을 지우고 가운데는 그대로 둔다")
	void strip() {
		assertThat(TextWhitespace.strip("﻿ 　\n\t 안녕  하세요 \t\n　 ﻿"))
			.isEqualTo("안녕  하세요");
	}

	@Test
	@DisplayName("strip은 공백만 있으면 빈 문자열을 돌려준다")
	void stripBlank() {
		assertThat(TextWhitespace.strip("  ﻿\n")).isEmpty();
	}

}
