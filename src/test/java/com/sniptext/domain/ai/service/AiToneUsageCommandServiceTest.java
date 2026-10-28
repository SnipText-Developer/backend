package com.sniptext.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.sniptext.domain.ai.entity.AiToneDailyUsage;
import com.sniptext.domain.ai.exception.AiErrorCode;
import com.sniptext.domain.ai.repository.AiToneDailyUsageRepository;
import com.sniptext.domain.ai.service.dto.result.AiToneReservation;
import com.sniptext.domain.user.entity.User;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

/**
 * 클래스에 @Transactional을 붙이지 않는다. 선점은 자기 트랜잭션으로 끝나야 하고, 동시 선점을 실제 커밋 순서로 검증해야 한다.
 * 대신 끝나면 만든 사용량과 회원을 지운다.
 */
@Import(MutableClockConfiguration.class)
class AiToneUsageCommandServiceTest extends IntegrationTestSupport {

	private static final int LIMIT = 10;
	/** DATETIME(6)과 같은 마이크로초 정밀도 */
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 10, 0, 0, 123_456_000);
	private static final LocalDate TODAY = NOW.toLocalDate();

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
		userId = saveUser("ai@sniptext.com");
	}

	@AfterEach
	void tearDown() {
		aiToneDailyUsageRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("오늘 행이 없으면 0회로 만들고 1회를 잡는다")
	void reserveFirst() {
		AiToneReservation reservation = aiToneUsageCommandService.reserve(userId, LIMIT);

		assertThat(reservation).isEqualTo(new AiToneReservation(userId, TODAY, 1));
		assertThat(usedCount(TODAY)).isEqualTo(1);
	}

	@Test
	@DisplayName("9회 사용 상태에서 선점하면 10회가 된다")
	void reserveUpToLimit() {
		reserveTimes(9);

		AiToneReservation reservation = aiToneUsageCommandService.reserve(userId, LIMIT);

		assertThat(reservation.usedCountAfter()).isEqualTo(10);
		assertThat(usedCount(TODAY)).isEqualTo(10);
	}

	@Test
	@DisplayName("10회 사용 상태에서 선점하면 AI429_1이고 횟수는 그대로다")
	void reserveOverLimit() {
		reserveTimes(10);

		assertThatThrownBy(() -> aiToneUsageCommandService.reserve(userId, LIMIT))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(AiErrorCode.DAILY_LIMIT_EXCEEDED);
		assertThat(usedCount(TODAY)).isEqualTo(10);
	}

	@Test
	@DisplayName("한도가 0이면 첫 선점부터 AI429_1이고 행도 남지 않는다")
	void reserveWithZeroLimit() {
		assertThatThrownBy(() -> aiToneUsageCommandService.reserve(userId, 0))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(AiErrorCode.DAILY_LIMIT_EXCEEDED);
		assertThat(aiToneDailyUsageRepository.findByUserIdAndUsageDate(userId, TODAY)).isEmpty();
	}

	@Test
	@DisplayName("다음 날 선점은 새 행에 잡히고 어제 행은 그대로다")
	void reserveOnNextDay() {
		reserveTimes(10);
		clock.setTo(NOW.plusDays(1));

		AiToneReservation reservation = aiToneUsageCommandService.reserve(userId, LIMIT);

		assertThat(reservation).isEqualTo(new AiToneReservation(userId, TODAY.plusDays(1), 1));
		assertThat(usedCount(TODAY)).isEqualTo(10);
	}

	@Test
	@DisplayName("다른 회원의 사용량은 내 선점에 영향을 주지 않는다")
	void otherUserIndependent() {
		Long otherUserId = saveUser("other@sniptext.com");
		for (int i = 0; i < 10; i++) {
			aiToneUsageCommandService.reserve(otherUserId, LIMIT);
		}

		assertThat(aiToneUsageCommandService.reserve(userId, LIMIT).usedCountAfter()).isEqualTo(1);
	}

	@Test
	@DisplayName("9회 사용 상태에서 두 요청이 동시에 선점하면 1건만 성공하고 최종 10회다")
	void concurrentReserve() throws Exception {
		reserveTimes(9);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			List<Future<Outcome>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					await(start);
					try {
						aiToneUsageCommandService.reserve(userId, LIMIT);
						return Outcome.RESERVED;
					} catch (BusinessException e) {
						return e.getResultCode() == AiErrorCode.DAILY_LIMIT_EXCEEDED ? Outcome.LIMIT_EXCEEDED : Outcome.OTHER;
					}
				}));
			}
			await(ready);
			start.countDown();

			List<Outcome> outcomes = new ArrayList<>();
			for (Future<Outcome> future : futures) {
				outcomes.add(future.get(30, TimeUnit.SECONDS));
			}

			assertThat(outcomes).containsExactlyInAnyOrder(Outcome.RESERVED, Outcome.LIMIT_EXCEEDED);
			assertThat(usedCount(TODAY)).isEqualTo(10);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("반환하면 1 줄어든다")
	void release() {
		reserveTimes(3);
		AiToneReservation reservation = aiToneUsageCommandService.reserve(userId, LIMIT);

		aiToneUsageCommandService.release(reservation);

		assertThat(usedCount(TODAY)).isEqualTo(3);
	}

	@Test
	@DisplayName("23:59:59에 선점하고 다음 날 00:00:05에 반환하면 어제 행에서 뺀다")
	void releaseAfterMidnight() {
		clock.setTo(LocalDateTime.of(2026, 10, 25, 23, 59, 59));
		AiToneReservation reservation = aiToneUsageCommandService.reserve(userId, LIMIT);
		clock.setTo(LocalDateTime.of(2026, 10, 26, 0, 0, 5));

		aiToneUsageCommandService.release(reservation);

		assertThat(usedCount(TODAY)).isZero();
		assertThat(aiToneDailyUsageRepository.findByUserIdAndUsageDate(userId, TODAY.plusDays(1))).isEmpty();
	}

	@Test
	@DisplayName("0회에서 반환해도 음수가 되지 않는다")
	void releaseAtZero() {
		AiToneReservation reservation = aiToneUsageCommandService.reserve(userId, LIMIT);
		aiToneUsageCommandService.release(reservation);

		aiToneUsageCommandService.release(reservation);

		assertThat(usedCount(TODAY)).isZero();
	}

	@Test
	@DisplayName("행이 없는 날짜로 반환해도 예외 없이 아무것도 바뀌지 않는다")
	void releaseWithoutRow() {
		aiToneUsageCommandService.release(new AiToneReservation(userId, TODAY, 1));

		assertThat(aiToneDailyUsageRepository.findByUserIdAndUsageDate(userId, TODAY)).isEmpty();
	}

	@Test
	@DisplayName("선점·반환은 updated_at을 그때의 Clock 시각으로 기록한다")
	void updatedAtFromClock() {
		AiToneReservation reservation = aiToneUsageCommandService.reserve(userId, LIMIT);
		assertThat(usage(TODAY).getUpdatedAt()).isEqualTo(NOW);

		clock.advance(Duration.ofMinutes(1));
		aiToneUsageCommandService.reserve(userId, LIMIT);
		assertThat(usage(TODAY).getUpdatedAt()).isEqualTo(NOW.plusMinutes(1));

		clock.advance(Duration.ofMinutes(1));
		aiToneUsageCommandService.release(reservation);
		assertThat(usage(TODAY).getUpdatedAt()).isEqualTo(NOW.plusMinutes(2));
	}

	@Test
	@DisplayName("한도에 걸린 선점은 updated_at을 바꾸지 않는다")
	void updatedAtUnchangedOnLimit() {
		reserveTimes(10);
		clock.advance(Duration.ofMinutes(1));

		assertThatThrownBy(() -> aiToneUsageCommandService.reserve(userId, LIMIT))
			.isInstanceOf(BusinessException.class);
		assertThat(usage(TODAY).getUpdatedAt()).isEqualTo(NOW);
	}

	private void reserveTimes(int times) {
		for (int i = 0; i < times; i++) {
			aiToneUsageCommandService.reserve(userId, LIMIT);
		}
	}

	private int usedCount(LocalDate date) {
		return usage(date).getUsedCount();
	}

	private AiToneDailyUsage usage(LocalDate date) {
		return aiToneDailyUsageRepository.findByUserIdAndUsageDate(userId, date).orElseThrow();
	}

	// TODO : 회원 픽스처는 가입 이슈에서 User.create와 함께 만든다. 그 전까지 기존 테스트처럼 직접 저장한다
	private Long saveUser(String email) {
		return userRepository.save(User.builder()
			.email(email)
			.nickname("닉네임")
			.build()).getId();
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("상대 스레드를 기다리다 시간이 초과됐습니다");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private enum Outcome {
		RESERVED, LIMIT_EXCEEDED, OTHER
	}

}
