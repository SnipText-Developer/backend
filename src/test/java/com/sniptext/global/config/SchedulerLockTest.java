package com.sniptext.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.TestLockedTask;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

/** 배치 잠금(셰드락)이 실제 MySQL 잠금 테이블로 같은 이름의 배치를 한 번만 실행시키는지 본다. */
class SchedulerLockTest extends IntegrationTestSupport {

	@Autowired
	private LockProvider lockProvider;

	@Autowired
	private TestLockedTask lockedTask;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		lockedTask.reset();
	}

	@Test
	@DisplayName("같은 이름의 잠금은 풀리기 전까지 다시 잡을 수 없고, 풀면 다시 잡을 수 있다")
	void lockIsExclusiveUntilReleased() {
		String name = "test-exclusive";

		Optional<SimpleLock> first = lockProvider.lock(lockFor(name));
		Optional<SimpleLock> second = lockProvider.lock(lockFor(name));

		assertThat(first).isPresent();
		assertThat(second).isEmpty();

		first.orElseThrow().unlock();
		Optional<SimpleLock> third = lockProvider.lock(lockFor(name));
		assertThat(third).isPresent();
		third.orElseThrow().unlock();
	}

	@Test
	@DisplayName("이름이 다른 배치는 서로의 잠금에 막히지 않는다")
	void differentNamesDoNotBlockEachOther() {
		Optional<SimpleLock> a = lockProvider.lock(lockFor("test-batch-a"));
		Optional<SimpleLock> b = lockProvider.lock(lockFor("test-batch-b"));

		assertThat(a).isPresent();
		assertThat(b).isPresent();
		a.orElseThrow().unlock();
		b.orElseThrow().unlock();
	}

	@Test
	@DisplayName("@SchedulerLock 메서드는 다른 서버가 같은 잠금을 쥐고 있으면 실행되지 않고, 풀린 뒤에는 실행된다")
	void annotatedMethodSkipsWhileLockedElsewhere() {
		SimpleLock heldByOtherServer = lockProvider.lock(lockFor(TestLockedTask.LOCK_NAME)).orElseThrow();

		lockedTask.run();
		assertThat(lockedTask.runs()).isZero();

		heldByOtherServer.unlock();
		lockedTask.run();
		lockedTask.run();
		assertThat(lockedTask.runs()).isEqualTo(2);
	}

	@Test
	@DisplayName("잠금 행은 shedlock 테이블에 배치 이름으로 남는다 (마이그레이션 테이블을 쓴다)")
	void lockRowIsStoredInShedlockTable() {
		lockProvider.lock(lockFor("test-row")).orElseThrow().unlock();

		Integer rows = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM shedlock WHERE name = ?", Integer.class, "test-row");
		assertThat(rows).isEqualTo(1);
	}

	private static LockConfiguration lockFor(String name) {
		return new LockConfiguration(Instant.now(), name, Duration.ofMinutes(1), Duration.ZERO);
	}

}
