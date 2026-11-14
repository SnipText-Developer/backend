package com.sniptext.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;

import jakarta.persistence.EntityManagerFactory;

/** 닉네임 조회(UserQueryService.getNickname). 로그인처럼 트랜잭션으로 감싸 실제로 커밋하고, 끝나면 회원을 지운다. */
class UserQueryServiceTest extends IntegrationTestSupport {

	@Autowired
	private UserQueryService userQueryService;

	@Autowired
	private UserCommandService userCommandService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@AfterEach
	void tearDown() {
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("닉네임을 돌려준다")
	void getNickname() {
		Long userId = userRepository.save(UserFixture.user("nick@gmail.com")).getId();

		assertThat(userQueryService.getNickname(userId)).isEqualTo(UserFixture.NICKNAME);
	}

	@Test
	@DisplayName("탈퇴했거나 없는 회원이면 USER404_1")
	void deletedOrUnknown() {
		Long deletedId = userRepository.save(UserFixture.deletedUser("gone@gmail.com", LocalDateTime.of(2026, 11, 1, 9, 0)))
			.getId();

		assertThatThrownBy(() -> userQueryService.getNickname(deletedId))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
		assertThatThrownBy(() -> userQueryService.getNickname(deletedId + 1000))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("로그인 시각 기록 뒤 같은 트랜잭션에서 닉네임을 읽으면 회원 조회 쿼리는 한 번만 나간다")
	void reusesLoadedUser() {
		Long userId = userRepository.save(UserFixture.user("once@gmail.com")).getId();
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		statistics.setStatisticsEnabled(true);
		try {
			long statements = transactionTemplate.execute(status -> {
				userCommandService.recordLogin(userId);
				userQueryService.getNickname(userId);
				return statistics.getPrepareStatementCount();
			});

			assertThat(statements).isEqualTo(1);
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

}
