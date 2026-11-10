package com.sniptext.domain.ai.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToneStyleTest {

	@Test
	@DisplayName("문체 상수 6개의 이름과 순서가 명세 6장과 같다 (상수 이름이 API 값)")
	void namesMatchSpec() {
		assertThat(Arrays.stream(ToneStyle.values()).map(Enum::name))
			.containsExactly("BOSS_EXTERNAL", "REPORT_OFFICIAL", "MARKETING", "NOTICE", "SOFT_POLITE", "CASUAL");
	}

}
