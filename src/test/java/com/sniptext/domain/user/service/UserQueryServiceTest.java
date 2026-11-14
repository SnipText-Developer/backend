package com.sniptext.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;

import jakarta.persistence.EntityManagerFactory;

@Transactional
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

	@Test
	@DisplayName("탈퇴하지 않은 회원이면 통과한다")
	void activeUser() {
		Long userId = userRepository.save(UserFixture.user("active@sniptext.com")).getId();

		assertThatCode(() -> userQueryService.validateActive(userId)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("없는 회원이면 USER404_1")
	void missingUser() {
		assertThatThrownBy(() -> userQueryService.validateActive(-1L))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("탈퇴 회원이면 USER404_1")
	void deletedUser() {
		Long userId = userRepository.save(
			UserFixture.deletedUser("deleted@sniptext.com", LocalDateTime.of(2026, 10, 1, 0, 0))).getId();

		assertThatThrownBy(() -> userQueryService.validateActive(userId))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("닉네임을 돌려준다")
	void getNickname() {
		Long userId = userRepository.save(UserFixture.user("nick@gmail.com")).getId();

		assertThat(userQueryService.getNickname(userId)).isEqualTo(UserFixture.NICKNAME);
	}

	@Test
	@DisplayName("탈퇴했거나 없는 회원의 닉네임을 찾으면 USER404_1")
	void nicknameOfDeletedOrUnknown() {
		Long deletedId = userRepository.save(UserFixture.deletedUser("gone@gmail.com", LocalDateTime.of(2026, 11, 1, 9, 0)))
			.getId();

		assertThatThrownBy(() -> userQueryService.getNickname(deletedId))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
		assertThatThrownBy(() -> userQueryService.getNickname(deletedId + 1000))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	/**
	 * 테스트 트랜잭션 안에서 회원을 저장하면 그 회원이 영속성 컨텍스트에 남아 실제 로그인과 쿼리 수가 달라진다.
	 * 그래서 이 테스트만 테스트 트랜잭션 없이 실행해 저장을 먼저 커밋하고, 끝나면 회원을 직접 지운다.
	 */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
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
			userRepository.deleteById(userId);
		}
	}

}
