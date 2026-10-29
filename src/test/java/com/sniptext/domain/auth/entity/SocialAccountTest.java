package com.sniptext.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SocialAccountTest {

	@Test
	@DisplayName("소셜 계정 연결을 만들면 받은 값이 그대로 들어간다")
	void create() {
		SocialAccount account = SocialAccount.create(1L, SocialProvider.GOOGLE, "109876543210987654321");

		assertThat(account.getUserId()).isEqualTo(1L);
		assertThat(account.getProvider()).isEqualTo(SocialProvider.GOOGLE);
		assertThat(account.getProviderUserId()).isEqualTo("109876543210987654321");
	}

	@Test
	@DisplayName("소셜 계정 고유값 100자는 만들 수 있고 101자는 IllegalArgumentException")
	void providerUserIdLength() {
		String max = "1".repeat(SocialAccount.PROVIDER_USER_ID_MAX_LENGTH);

		assertThat(SocialAccount.create(1L, SocialProvider.GOOGLE, max).getProviderUserId()).hasSize(100);
		assertThatThrownBy(() -> SocialAccount.create(1L, SocialProvider.GOOGLE, max + "1"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" "})
	@DisplayName("소셜 계정 고유값이 없거나 공백이면 IllegalArgumentException")
	void blankProviderUserId(String providerUserId) {
		assertThatThrownBy(() -> SocialAccount.create(1L, SocialProvider.GOOGLE, providerUserId))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("회원 ID·제공자가 없으면 IllegalArgumentException")
	void missingValues() {
		assertThatThrownBy(() -> SocialAccount.create(null, SocialProvider.GOOGLE, "1"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SocialAccount.create(1L, null, "1"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
