package com.sniptext.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.sniptext.domain.ai.repository.AiToneDailyUsageRepository;
import com.sniptext.domain.ai.service.dto.result.AiToneUsageResult;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

/** 선점이 자기 트랜잭션으로 커밋되므로 클래스에 @Transactional을 붙이지 않는다. */
@Import(MutableClockConfiguration.class)
class AiToneUsageQueryServiceTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 10, 0);
	private static final LocalDateTime NEXT_MIDNIGHT = LocalDateTime.of(2026, 10, 12, 0, 0);

	@Autowired
	private AiToneUsageQueryService aiToneUsageQueryService;

	@Autowired
	private AiToneUsageCommandService aiToneUsageCommandService;

	@Autowired
	private AiToneDailyUsageRepository aiToneDailyUsageRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MutableClock clock;

	private Long userId;

	@BeforeEach
	void setUp() {
		clock.setTo(NOW);
		userId = userRepository.save(UserFixture.user("usage@sniptext.com")).getId();
	}

	@AfterEach
	void tearDown() {
		aiToneDailyUsageRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("오늘 행이 없으면 0/5, 남은 5")
	void withoutRow() {
		assertThat(aiToneUsageQueryService.getTodayUsage(userId))
			.isEqualTo(new AiToneUsageResult(0, 5, 5, NEXT_MIDNIGHT));
	}

	@Test
	@DisplayName("오늘 4회 썼으면 4/5, 남은 1")
	void usedFour() {
		reserveTimes(4);

		assertThat(aiToneUsageQueryService.getTodayUsage(userId))
			.isEqualTo(new AiToneUsageResult(4, 5, 1, NEXT_MIDNIGHT));
	}

	@Test
	@DisplayName("오늘 5회 모두 썼으면 5/5, 남은 0")
	void usedAll() {
		reserveTimes(5);

		assertThat(aiToneUsageQueryService.getTodayUsage(userId))
			.isEqualTo(new AiToneUsageResult(5, 5, 0, NEXT_MIDNIGHT));
	}

	@Test
	@DisplayName("어제 사용량은 오늘 사용량에 들어가지 않는다")
	void yesterdayNotCounted() {
		reserveTimes(1);
		clock.setTo(NOW.plusDays(1));

		AiToneUsageResult result = aiToneUsageQueryService.getTodayUsage(userId);

		assertThat(result.usedCount()).isZero();
		assertThat(result.remaining()).isEqualTo(5);
	}

	@Test
	@DisplayName("23:59:59.999999의 리셋 시각은 다음 날 00:00, 자정이 되면 그다음 날 00:00")
	void resetAtBoundary() {
		clock.setTo(LocalDateTime.of(2026, 10, 11, 23, 59, 59, 999_999_000));
		assertThat(aiToneUsageQueryService.getTodayUsage(userId).resetAt()).isEqualTo(NEXT_MIDNIGHT);

		clock.setTo(NEXT_MIDNIGHT);
		assertThat(aiToneUsageQueryService.getTodayUsage(userId).resetAt()).isEqualTo(NEXT_MIDNIGHT.plusDays(1));
	}

	private void reserveTimes(int times) {
		for (int i = 0; i < times; i++) {
			aiToneUsageCommandService.reserve(userId);
		}
	}

}
