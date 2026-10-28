package com.sniptext.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.sniptext.domain.ai.repository.AiToneDailyUsageRepository;
import com.sniptext.domain.ai.service.dto.result.AiToneUsageResult;
import com.sniptext.domain.user.entity.Plan;
import com.sniptext.domain.user.entity.User;
import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

/** 한도는 실제 현재 플랜 조회를 거쳐 얻는다. 선점이 자기 트랜잭션으로 커밋되므로 클래스에 @Transactional을 붙이지 않는다. */
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

	@BeforeEach
	void setUp() {
		clock.setTo(NOW);
	}

	@AfterEach
	void tearDown() {
		aiToneDailyUsageRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("FREE면 쓸 수 없고 한도 0, 사용 0, 남은 0")
	void free() {
		Long userId = saveUser(null);

		assertThat(aiToneUsageQueryService.getTodayUsage(userId))
			.isEqualTo(new AiToneUsageResult(false, 0, 0, 0, NEXT_MIDNIGHT));
	}

	@Test
	@DisplayName("PRO에 오늘 행이 없으면 0/10, 남은 10")
	void proWithoutRow() {
		Long userId = saveUser(Plan.PRO);

		assertThat(aiToneUsageQueryService.getTodayUsage(userId))
			.isEqualTo(new AiToneUsageResult(true, 0, 10, 10, NEXT_MIDNIGHT));
	}

	@Test
	@DisplayName("PRO가 오늘 4회 썼으면 4/10, 남은 6")
	void proUsedFour() {
		Long userId = saveUser(Plan.PRO);
		for (int i = 0; i < 4; i++) {
			aiToneUsageCommandService.reserve(userId, 10);
		}

		assertThat(aiToneUsageQueryService.getTodayUsage(userId))
			.isEqualTo(new AiToneUsageResult(true, 4, 10, 6, NEXT_MIDNIGHT));
	}

	@Test
	@DisplayName("어제 사용량은 오늘 사용량에 들어가지 않는다")
	void yesterdayNotCounted() {
		Long userId = saveUser(Plan.PRO);
		aiToneUsageCommandService.reserve(userId, 10);
		clock.setTo(NOW.plusDays(1));

		AiToneUsageResult result = aiToneUsageQueryService.getTodayUsage(userId);

		assertThat(result.usedCount()).isZero();
		assertThat(result.remaining()).isEqualTo(10);
	}

	@Test
	@DisplayName("23:59:59.999999의 리셋 시각은 다음 날 00:00, 자정이 되면 그다음 날 00:00")
	void resetAtBoundary() {
		Long userId = saveUser(Plan.PRO);

		clock.setTo(LocalDateTime.of(2026, 10, 11, 23, 59, 59, 999_999_000));
		assertThat(aiToneUsageQueryService.getTodayUsage(userId).resetAt()).isEqualTo(NEXT_MIDNIGHT);

		clock.setTo(NEXT_MIDNIGHT);
		assertThat(aiToneUsageQueryService.getTodayUsage(userId).resetAt()).isEqualTo(NEXT_MIDNIGHT.plusDays(1));
	}

	@Test
	@DisplayName("없는 회원이면 USER404_1")
	void missingUser() {
		assertThatThrownBy(() -> aiToneUsageQueryService.getTodayUsage(-1L))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("탈퇴 회원이면 USER404_1")
	void deletedUser() {
		Long userId = userRepository.save(User.builder()
			.email("deleted@sniptext.com")
			.nickname("탈퇴회원")
			.planOverride(Plan.PRO)
			.deletedAt(LocalDateTime.of(2026, 10, 1, 0, 0))
			.build()).getId();

		assertThatThrownBy(() -> aiToneUsageQueryService.getTodayUsage(userId))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	// TODO : 회원 픽스처는 가입 이슈에서 User.create와 함께 만든다. 그 전까지 기존 테스트처럼 직접 저장한다
	private Long saveUser(Plan planOverride) {
		return userRepository.save(User.builder()
			.email("usage@sniptext.com")
			.nickname("닉네임")
			.planOverride(planOverride)
			.build()).getId();
	}

}
