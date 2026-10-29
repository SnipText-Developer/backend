package com.sniptext.global.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.user.entity.User;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.persistence.EntityManager;

/** 시각 자동 기록을 users 테이블(BaseTimeEntity 상속)로 검증한다. */
@Transactional
@Import(MutableClockConfiguration.class)
class BaseTimeEntityTest extends IntegrationTestSupport {

	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 25, 10, 0);

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
	}

	@Test
	@DisplayName("저장하면 created_at과 updated_at이 Clock의 고정 시각으로 같다")
	void createdAndUpdatedOnSave() {
		User user = saveUser("save@sniptext.com");

		User found = reload(user);

		assertThat(found.getCreatedAt()).isEqualTo(FIXED);
		assertThat(found.getUpdatedAt()).isEqualTo(FIXED);
	}

	@Test
	@DisplayName("Clock을 앞으로 옮긴 뒤 수정하면 updated_at만 바뀌고 created_at은 그대로다")
	void updatedOnlyOnModify() {
		User user = saveUser("modify@sniptext.com");
		clock.advance(Duration.ofHours(1));

		User managed = entityManager.find(User.class, user.getId());
		// 이번 이슈에 회원 값 변경 메서드가 없어서, 변경 감지를 일으키려고 필드를 직접 바꾼다
		ReflectionTestUtils.setField(managed, "nickname", "바뀐닉네임");
		entityManager.flush();
		entityManager.clear();

		User found = entityManager.find(User.class, user.getId());
		assertThat(found.getCreatedAt()).isEqualTo(FIXED);
		assertThat(found.getUpdatedAt()).isEqualTo(FIXED.plusHours(1));
	}

	@Test
	@DisplayName("나노초가 있는 시각으로 저장해도 다시 읽은 값이 저장 전 엔티티 값과 정확히 같다")
	void truncatedToMicros() {
		clock.setTo(LocalDateTime.of(2026, 10, 25, 10, 0, 0, 123_456_789));

		User user = saveUser("nanos@sniptext.com");
		LocalDateTime createdBeforeReload = user.getCreatedAt();
		LocalDateTime updatedBeforeReload = user.getUpdatedAt();

		User found = reload(user);

		assertThat(createdBeforeReload).isEqualTo(LocalDateTime.of(2026, 10, 25, 10, 0, 0, 123_456_000));
		assertThat(found.getCreatedAt()).isEqualTo(createdBeforeReload);
		assertThat(found.getUpdatedAt()).isEqualTo(updatedBeforeReload);
	}

	private User saveUser(String email) {
		User user = UserFixture.user(email);
		entityManager.persist(user);
		entityManager.flush();
		return user;
	}

	private User reload(User user) {
		entityManager.clear();
		return entityManager.find(User.class, user.getId());
	}

}
