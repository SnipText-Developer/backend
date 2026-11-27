package com.sniptext.domain.auth.service;

import static com.sniptext.fixture.RefreshTokenFixture.hash;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import com.sniptext.domain.auth.repository.RefreshTokenRepository;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.RefreshTokenFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.config.TimeConfig;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

/**
 * 04:00 정리 배치. 테스트에서는 스케줄이 꺼져 있어(SNIPTEXT_SCHEDULING_ENABLED=false) 메서드를 직접 부른다.
 * 잠금은 스케줄과 따로 걸리므로 다른 서버가 잠금을 쥐고 있을 때 건너뛰는지도 여기서 본다.
 */
@Import(MutableClockConfiguration.class)
class RefreshTokenCleanupSchedulerTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 28, 4, 0);

	@Autowired
	private RefreshTokenCleanupScheduler scheduler;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LockProvider lockProvider;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MutableClock clock;

	private Long userId;

	@BeforeEach
	void setUp() {
		releaseLock();
		clock.setTo(NOW);
		userId = userRepository.save(UserFixture.user("scheduler@gmail.com")).getId();
		refreshTokenRepository.save(RefreshTokenFixture.webExpiringAt(userId, hash(1), NOW.minusDays(1)));
		refreshTokenRepository.save(RefreshTokenFixture.webExpiringAt(userId, hash(2), NOW.plusDays(1)));
	}

	@AfterEach
	void tearDown() {
		releaseLock();
		userRepository.deleteAllInBatch();
	}

	/**
	 * lockAtLeastFor(1분) 때문에 앞 테스트의 잠금이 다음 테스트의 실행을 막지 않게 푼다.
	 * 행을 지우면 안 된다. 셰드락은 한 번 만든 잠금 행을 기억하고 다음부터는 고치기만 해서, 행이 없으면 잠금을 못 잡는다.
	 */
	private void releaseLock() {
		jdbcTemplate.update("UPDATE shedlock SET lock_until = locked_at WHERE name = ?", RefreshTokenCleanupScheduler.LOCK_NAME);
	}

	@Test
	@DisplayName("매일 04:00 한국 시간으로 실행되도록 걸려 있다")
	void scheduledAtFourKst() throws NoSuchMethodException {
		Scheduled scheduled = RefreshTokenCleanupScheduler.class
			.getMethod("deleteExpiredRefreshTokens")
			.getAnnotation(Scheduled.class);

		assertThat(scheduled.cron()).isEqualTo("0 0 4 * * *");
		assertThat(scheduled.zone()).isEqualTo(TimeConfig.KST_ZONE_ID);
	}

	@Test
	@DisplayName("실행하면 만료된 토큰만 지운다")
	void deletesExpiredTokens() {
		scheduler.deleteExpiredRefreshTokens();

		assertThat(refreshTokenRepository.findAll()).singleElement()
			.satisfies(token -> assertThat(token.getTokenHash()).isEqualTo(hash(2)));
	}

	@Test
	@DisplayName("다른 서버가 같은 잠금을 쥐고 있으면 지우지 않고 건너뛴다")
	void skipsWhileLockedElsewhere() {
		SimpleLock heldByOtherServer = lockProvider.lock(new LockConfiguration(
			Instant.now(), RefreshTokenCleanupScheduler.LOCK_NAME, Duration.ofMinutes(1), Duration.ZERO)).orElseThrow();
		try {
			scheduler.deleteExpiredRefreshTokens();

			assertThat(refreshTokenRepository.count()).isEqualTo(2);
		} finally {
			heldByOtherServer.unlock();
		}
	}

	@Test
	@DisplayName("한 번 돈 뒤 1분 안에 다시 불려도 다시 돌지 않는다 (lockAtLeastFor)")
	void doesNotRunTwiceWithinOneMinute() {
		scheduler.deleteExpiredRefreshTokens();
		refreshTokenRepository.save(RefreshTokenFixture.webExpiringAt(userId, hash(3), NOW.minusDays(1)));

		scheduler.deleteExpiredRefreshTokens();

		assertThat(refreshTokenRepository.findAll())
			.extracting(token -> token.getTokenHash())
			.containsExactlyInAnyOrder(hash(2), hash(3));
	}

}
