package com.sniptext.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.user.entity.Plan;
import com.sniptext.domain.user.entity.User;
import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;

@Transactional
class UserQueryServiceTest extends IntegrationTestSupport {

	@Autowired
	private UserQueryService userQueryService;

	@Autowired
	private UserRepository userRepository;

	@Test
	@DisplayName("플랜 지정값이 있으면 그 값을, 없으면 빈 값을 돌려준다")
	void findPlanOverride() {
		User pro = userRepository.save(user("pro@sniptext.com", Plan.PRO, null));
		User none = userRepository.save(user("none@sniptext.com", null, null));

		assertThat(userQueryService.findPlanOverride(pro.getId())).contains(Plan.PRO);
		assertThat(userQueryService.findPlanOverride(none.getId())).isEmpty();
	}

	@Test
	@DisplayName("없는 회원의 플랜 지정값을 찾으면 USER404_1")
	void missingUser() {
		assertThatThrownBy(() -> userQueryService.findPlanOverride(-1L))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("탈퇴 회원의 플랜 지정값을 찾으면 USER404_1")
	void deletedUser() {
		User deleted = userRepository.save(user("deleted@sniptext.com", Plan.PRO, LocalDateTime.of(2026, 10, 1, 0, 0)));

		assertThatThrownBy(() -> userQueryService.findPlanOverride(deleted.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

	private User user(String email, Plan planOverride, LocalDateTime deletedAt) {
		return User.builder()
			.email(email)
			.nickname("회원")
			.planOverride(planOverride)
			.deletedAt(deletedAt)
			.build();
	}

}
