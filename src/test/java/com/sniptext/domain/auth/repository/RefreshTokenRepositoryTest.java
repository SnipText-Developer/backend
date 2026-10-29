package com.sniptext.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.sniptext.fixture.RefreshTokenFixture.hash;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.auth.entity.RefreshToken;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.RefreshTokenFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;

@Transactional
class RefreshTokenRepositoryTest extends IntegrationTestSupport {

	private static final String FAMILY_A = "aaaaaaaa-0000-0000-0000-000000000001";
	private static final String FAMILY_B = "bbbbbbbb-0000-0000-0000-000000000002";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 9, 0);

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private UserRepository userRepository;

	private Long userId;
	private Long otherUserId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(UserFixture.user("token@gmail.com")).getId();
		otherUserId = userRepository.save(UserFixture.user("other@gmail.com")).getId();
	}

	@Test
	@DisplayName("해시로 재발급 토큰을 찾는다 (CHAR 컬럼 매핑 확인 포함)")
	void findByTokenHash() {
		refreshTokenRepository.save(RefreshTokenFixture.web(userId, hash(1)));

		assertThat(refreshTokenRepository.findByTokenHash(hash(1)))
			.map(RefreshToken::getFamilyId)
			.contains(RefreshTokenFixture.FAMILY_ID);
		assertThat(refreshTokenRepository.findByTokenHash(hash(2))).isEmpty();
	}

	@Test
	@DisplayName("같은 해시를 두 번 저장하면 DB 유니크 위반이다")
	void uniqueTokenHash() {
		refreshTokenRepository.save(RefreshTokenFixture.web(userId, hash(1)));

		assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(RefreshTokenFixture.web(otherUserId, hash(1))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("계보 폐기는 그 계보의 살아 있는 토큰만 폐기하고, 이미 폐기된 토큰의 시각은 그대로 둔다")
	void revokeFamily() {
		RefreshToken alreadyRevoked = RefreshTokenFixture.web(userId, hash(1), FAMILY_A);
		alreadyRevoked.revoke(NOW.minusDays(1));
		refreshTokenRepository.save(alreadyRevoked);
		refreshTokenRepository.save(RefreshTokenFixture.web(userId, hash(2), FAMILY_A));
		refreshTokenRepository.save(RefreshTokenFixture.web(userId, hash(3), FAMILY_B));

		int revoked = refreshTokenRepository.revokeFamily(FAMILY_A, NOW);

		assertThat(revoked).isEqualTo(1);
		assertThat(revokedAt(hash(1))).isEqualTo(NOW.minusDays(1));
		assertThat(revokedAt(hash(2))).isEqualTo(NOW);
		assertThat(revokedAt(hash(3))).isNull();
	}

	@Test
	@DisplayName("로그아웃 폐기는 그 회원의 웹·확장 토큰을 모두 폐기하고 다른 회원 토큰은 건드리지 않는다")
	void revokeAllByUserId() {
		refreshTokenRepository.save(RefreshTokenFixture.web(userId, hash(1), FAMILY_A));
		refreshTokenRepository.save(RefreshTokenFixture.extension(userId, hash(2), FAMILY_B));
		refreshTokenRepository.save(RefreshTokenFixture.web(otherUserId, hash(3), RefreshTokenFixture.FAMILY_ID));

		int revoked = refreshTokenRepository.revokeAllByUserId(userId, NOW);

		assertThat(revoked).isEqualTo(2);
		assertThat(revokedAt(hash(1))).isEqualTo(NOW);
		assertThat(revokedAt(hash(2))).isEqualTo(NOW);
		assertThat(revokedAt(hash(3))).isNull();
	}

	private LocalDateTime revokedAt(String tokenHash) {
		return refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow().getRevokedAt();
	}

}
