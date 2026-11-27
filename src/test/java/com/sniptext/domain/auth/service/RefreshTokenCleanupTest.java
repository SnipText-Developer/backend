package com.sniptext.domain.auth.service;

import static com.sniptext.fixture.RefreshTokenFixture.hash;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.sniptext.domain.auth.entity.RefreshToken;
import com.sniptext.domain.auth.repository.RefreshTokenRepository;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.RefreshTokenFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

/**
 * 만료 재발급 토큰 정리 (04:00 배치가 부르는 서비스). 나눠 지울 때마다 트랜잭션이 커밋되는지 보려고
 * 클래스에 @Transactional을 붙이지 않고, 회원을 직접 지운다 (토큰은 외래키 CASCADE로 함께 지워진다).
 */
@Import(MutableClockConfiguration.class)
class RefreshTokenCleanupTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 4, 0);

	@Autowired
	private AuthCommandService authCommandService;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MutableClock clock;

	private Long userId;

	@BeforeEach
	void setUp() {
		clock.setTo(NOW);
		userId = userRepository.save(UserFixture.user("cleanup@gmail.com")).getId();
	}

	@AfterEach
	void tearDown() {
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("만료된 토큰을 정한 개수씩 나눠 모두 지우고 지운 개수를 돌려주며, 만료 전 토큰은 남긴다")
	void deletesInChunks() {
		refreshTokenRepository.saveAll(List.of(
			RefreshTokenFixture.webExpiringAt(userId, hash(1), NOW.minusDays(5)),
			RefreshTokenFixture.webExpiringAt(userId, hash(2), NOW.minusDays(4)),
			RefreshTokenFixture.webExpiringAt(userId, hash(3), NOW.minusDays(3)),
			RefreshTokenFixture.webExpiringAt(userId, hash(4), NOW.minusDays(2)),
			RefreshTokenFixture.webExpiringAt(userId, hash(5), NOW),
			RefreshTokenFixture.webExpiringAt(userId, hash(6), NOW.plusNanos(1_000))));

		int deleted = authCommandService.deleteExpiredRefreshTokens(2);

		assertThat(deleted).isEqualTo(5);
		assertThat(refreshTokenRepository.findAll()).extracting(RefreshToken::getTokenHash).containsExactly(hash(6));
	}

	@Test
	@DisplayName("지울 토큰이 없으면 0을 돌려준다")
	void nothingToDelete() {
		refreshTokenRepository.save(RefreshTokenFixture.webExpiringAt(userId, hash(1), NOW.plusDays(1)));

		assertThat(authCommandService.deleteExpiredRefreshTokens()).isZero();
		assertThat(refreshTokenRepository.count()).isEqualTo(1);
	}

}
