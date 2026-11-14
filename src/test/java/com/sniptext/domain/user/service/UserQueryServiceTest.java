package com.sniptext.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;

@Transactional
class UserQueryServiceTest extends IntegrationTestSupport {

	@Autowired
	private UserQueryService userQueryService;

	@Autowired
	private UserRepository userRepository;

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

}
