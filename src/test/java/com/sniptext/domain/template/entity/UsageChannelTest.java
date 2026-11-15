package com.sniptext.domain.template.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UsageChannelTest {

	@Test
	@DisplayName("상수 이름이 명세 공통 규칙 6장의 사용 채널 값과 같다")
	void namesMatchSpec() {
		assertThat(UsageChannel.values())
			.extracting(Enum::name)
			.containsExactly("DASHBOARD_COPY", "POPUP_COPY", "EMAIL_INLINE");
	}

	@ParameterizedTest
	@ValueSource(strings = {"DASHBOARD_COPY", "POPUP_COPY", "EMAIL_INLINE"})
	@DisplayName("명세의 값은 지원한다")
	void supported(String value) {
		assertThat(UsageChannel.isSupported(value)).isTrue();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "dashboard_copy", "Dashboard_Copy", " DASHBOARD_COPY", "DASHBOARD_COPY ", "1",
		"0", "COPY", "dashboard"})
	@DisplayName("없거나 대소문자·공백이 다르거나 순번 숫자면 지원하지 않는다")
	void unsupported(String value) {
		assertThat(UsageChannel.isSupported(value)).isFalse();
	}

}
