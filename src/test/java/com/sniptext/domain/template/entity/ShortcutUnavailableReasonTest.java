package com.sniptext.domain.template.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ShortcutUnavailableReasonTest {

	@Test
	@DisplayName("상수 이름이 명세 13번 reason 값과 같다")
	void namesMatchSpec() {
		assertThat(ShortcutUnavailableReason.values())
			.extracting(Enum::name)
			.containsExactly("INVALID_FORMAT", "RESERVED", "DUPLICATED");
	}

	@ParameterizedTest
	@EnumSource(value = ShortcutViolation.class, names = {"EMPTY", "INVALID_FORMAT"})
	@DisplayName("빈 값과 형식 위반은 INVALID_FORMAT이다 (확인 API에는 빈 값 사유가 따로 없다)")
	void emptyAndInvalidFormat(ShortcutViolation violation) {
		assertThat(ShortcutUnavailableReason.from(violation)).isEqualTo(ShortcutUnavailableReason.INVALID_FORMAT);
	}

	@Test
	@DisplayName("예약어 위반은 RESERVED다")
	void reserved() {
		assertThat(ShortcutUnavailableReason.from(ShortcutViolation.RESERVED))
			.isEqualTo(ShortcutUnavailableReason.RESERVED);
	}

}
