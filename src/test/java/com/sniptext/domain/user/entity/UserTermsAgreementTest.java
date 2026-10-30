package com.sniptext.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTermsAgreementTest {

	private static final LocalDateTime AGREED_AT = LocalDateTime.of(2026, 10, 25, 10, 0);

	@Test
	@DisplayName("약관 동의를 만들면 받은 값이 그대로 들어간다")
	void create() {
		UserTermsAgreement agreement = UserTermsAgreement.create(1L, TermsType.SERVICE, "v1", AGREED_AT);

		assertThat(agreement.getUserId()).isEqualTo(1L);
		assertThat(agreement.getTermsType()).isEqualTo(TermsType.SERVICE);
		assertThat(agreement.getVersion()).isEqualTo("v1");
		assertThat(agreement.getAgreedAt()).isEqualTo(AGREED_AT);
	}

	@Test
	@DisplayName("버전 20자는 만들 수 있고 21자는 IllegalArgumentException")
	void versionLength() {
		String max = "v".repeat(UserTermsAgreement.VERSION_MAX_LENGTH);

		assertThat(UserTermsAgreement.create(1L, TermsType.PRIVACY, max, AGREED_AT).getVersion()).hasSize(20);
		assertThatThrownBy(() -> UserTermsAgreement.create(1L, TermsType.PRIVACY, max + "1", AGREED_AT))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" "})
	@DisplayName("버전이 없거나 공백이면 IllegalArgumentException")
	void blankVersion(String version) {
		assertThatThrownBy(() -> UserTermsAgreement.create(1L, TermsType.SERVICE, version, AGREED_AT))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("회원 ID·약관 종류·동의 시각이 없으면 IllegalArgumentException")
	void missingValues() {
		assertThatThrownBy(() -> UserTermsAgreement.create(null, TermsType.SERVICE, "v1", AGREED_AT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> UserTermsAgreement.create(1L, null, "v1", AGREED_AT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> UserTermsAgreement.create(1L, TermsType.SERVICE, "v1", null))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
