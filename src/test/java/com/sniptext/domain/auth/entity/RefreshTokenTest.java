package com.sniptext.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sniptext.fixture.RefreshTokenFixture;

class RefreshTokenTest {

	private static final String HASH = RefreshTokenFixture.hash(1);
	private static final String FAMILY_ID = RefreshTokenFixture.FAMILY_ID;
	private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 8, 10, 0);

	@Test
	@DisplayName("재발급 토큰을 만들면 받은 값이 들어가고 폐기되지 않은 상태로 시작한다")
	void create() {
		RefreshToken token = RefreshToken.create(1L, ClientType.WEB, HASH, FAMILY_ID, EXPIRES_AT);

		assertThat(token.getUserId()).isEqualTo(1L);
		assertThat(token.getClientType()).isEqualTo(ClientType.WEB);
		assertThat(token.getTokenHash()).isEqualTo(HASH);
		assertThat(token.getFamilyId()).isEqualTo(FAMILY_ID);
		assertThat(token.getExpiresAt()).isEqualTo(EXPIRES_AT);
		assertThat(token.isRevoked()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(ints = {63, 65})
	@DisplayName("해시가 64자가 아니면 IllegalArgumentException이고 메시지에 해시를 넣지 않는다")
	void hashLength(int length) {
		String hash = "a".repeat(length);

		assertThatThrownBy(() -> RefreshToken.create(1L, ClientType.WEB, hash, FAMILY_ID, EXPIRES_AT))
			.isInstanceOf(IllegalArgumentException.class)
			.message().doesNotContain(hash);
	}

	@Test
	@DisplayName("계보 ID가 36자가 아니거나 필수 값이 없으면 IllegalArgumentException")
	void missingValues() {
		assertThatThrownBy(() -> RefreshToken.create(1L, ClientType.WEB, HASH, "short", EXPIRES_AT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RefreshToken.create(null, ClientType.WEB, HASH, FAMILY_ID, EXPIRES_AT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RefreshToken.create(1L, null, HASH, FAMILY_ID, EXPIRES_AT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RefreshToken.create(1L, ClientType.WEB, HASH, FAMILY_ID, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("두 번 폐기해도 처음 폐기한 시각이 남는다")
	void revokeOnce() {
		RefreshToken token = RefreshToken.create(1L, ClientType.WEB, HASH, FAMILY_ID, EXPIRES_AT);
		LocalDateTime first = LocalDateTime.of(2026, 10, 30, 9, 0);

		token.revoke(first);
		token.revoke(first.plusHours(1));

		assertThat(token.isRevoked()).isTrue();
		assertThat(token.getRevokedAt()).isEqualTo(first);
	}

	@Test
	@DisplayName("만료 시각 1마이크로초 전에는 유효하고, 만료 시각부터는 만료다")
	void expiryBoundary() {
		RefreshToken token = RefreshToken.create(1L, ClientType.WEB, HASH, FAMILY_ID, EXPIRES_AT);

		assertThat(token.isExpired(EXPIRES_AT.minusNanos(1_000))).isFalse();
		assertThat(token.isExpired(EXPIRES_AT)).isTrue();
	}

}
