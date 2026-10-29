package com.sniptext.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.user.entity.User;
import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;

/**
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 잠금이 테스트 트랜잭션에 묶여 동시 잠금을 검증할 수 없다.
 * 대신 각 호출을 TransactionTemplate으로 감싸고, 끝나면 만든 회원을 지운다.
 */
class UserCommandServiceTest extends IntegrationTestSupport {

	/** 첫 번째 트랜잭션이 잠금을 쥐고 버티는 시간. 이 안에 두 번째가 잠금을 얻으면 잠금이 동작하지 않는 것이다. */
	private static final long HOLD_MILLIS = 1_000;

	@Autowired
	private UserCommandService userCommandService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@AfterEach
	void tearDown() {
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("없는 회원을 잠그면 USER404_1")
	void lockMissingUser() {
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> userCommandService.lockUser(-1L)))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("탈퇴 회원을 잠그면 USER404_1")
	void lockDeletedUser() {
		User deleted = userRepository.save(
			UserFixture.deletedUser("deleted@sniptext.com", LocalDateTime.of(2026, 10, 1, 0, 0)));

		assertThatThrownBy(
			() -> transactionTemplate.executeWithoutResult(status -> userCommandService.lockUser(deleted.getId())))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("트랜잭션 밖에서 lockUser를 부르면 IllegalTransactionStateException")
	void lockOutsideTransaction() {
		User user = saveUser();

		assertThatThrownBy(() -> userCommandService.lockUser(user.getId()))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	@DisplayName("같은 회원을 두 트랜잭션에서 잠그면 두 번째는 첫 번째가 커밋할 때까지 기다린다")
	void secondLockWaitsForFirstCommit() throws Exception {
		Long userId = saveUser().getId();
		CountDownLatch firstLocked = new CountDownLatch(1);
		CountDownLatch secondRequesting = new CountDownLatch(1);
		CountDownLatch secondAcquired = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<FirstResult> first = executor.submit(() -> transactionTemplate.execute(status -> {
				userCommandService.lockUser(userId);
				firstLocked.countDown();
				await(secondRequesting);
				// 두 번째가 잠금을 요청한 뒤 잠금을 쥔 채 버틴다. 잠금이 동작하면 이 시간 안에 두 번째가 잠금을 얻지 못한다
				boolean acquiredWhileHolding = awaitFor(secondAcquired, HOLD_MILLIS);
				// 콜백의 마지막 지점 = 커밋 직전. 이 시각 뒤에 커밋이 일어나므로, 두 번째의 획득 시각이 이보다 늦어야 한다
				return new FirstResult(acquiredWhileHolding, System.nanoTime());
			}));

			Future<Long> second = executor.submit(() -> {
				await(firstLocked);
				return transactionTemplate.execute(status -> {
					secondRequesting.countDown();
					userCommandService.lockUser(userId);
					long acquiredAt = System.nanoTime();
					secondAcquired.countDown();
					return acquiredAt;
				});
			});

			FirstResult firstResult = first.get(30, TimeUnit.SECONDS);
			long secondAcquiredAt = second.get(30, TimeUnit.SECONDS);

			assertThat(firstResult.secondAcquiredWhileHolding()).isFalse();
			assertThat(secondAcquiredAt).isGreaterThan(firstResult.commitStartedAt());
		} finally {
			executor.shutdownNow();
		}
	}

	private User saveUser() {
		return userRepository.save(UserFixture.user("lock@sniptext.com"));
	}

	private static void await(CountDownLatch latch) {
		if (!awaitFor(latch, 10_000)) {
			throw new IllegalStateException("상대 스레드를 기다리다 시간이 초과됐습니다");
		}
	}

	private static boolean awaitFor(CountDownLatch latch, long millis) {
		try {
			return latch.await(millis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private record FirstResult(boolean secondAcquiredWhileHolding, long commitStartedAt) {
	}

}
