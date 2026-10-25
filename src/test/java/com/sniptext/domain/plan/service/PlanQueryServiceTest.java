package com.sniptext.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sniptext.domain.plan.service.dto.result.PlanResult;
import com.sniptext.domain.user.entity.Plan;
import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.service.UserQueryService;
import com.sniptext.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class PlanQueryServiceTest {

	private static final Long USER_ID = 1L;

	@Mock
	private UserQueryService userQueryService;

	@InjectMocks
	private PlanQueryService planQueryService;

	@Test
	@DisplayName("플랜 지정값이 없으면 FREE, 템플릿 5개, AI 하루 0회")
	void noOverride() {
		given(userQueryService.findPlanOverride(USER_ID)).willReturn(Optional.empty());

		assertThat(planQueryService.getCurrentPlan(USER_ID)).isEqualTo(new PlanResult(Plan.FREE, 5, 0));
	}

	@Test
	@DisplayName("플랜 지정값이 FREE면 FREE, 템플릿 5개, AI 하루 0회")
	void freeOverride() {
		given(userQueryService.findPlanOverride(USER_ID)).willReturn(Optional.of(Plan.FREE));

		assertThat(planQueryService.getCurrentPlan(USER_ID)).isEqualTo(new PlanResult(Plan.FREE, 5, 0));
	}

	@Test
	@DisplayName("플랜 지정값이 PRO면 PRO, 템플릿 50개, AI 하루 10회")
	void proOverride() {
		given(userQueryService.findPlanOverride(USER_ID)).willReturn(Optional.of(Plan.PRO));

		assertThat(planQueryService.getCurrentPlan(USER_ID)).isEqualTo(new PlanResult(Plan.PRO, 50, 10));
	}

	@Test
	@DisplayName("회원이 없으면 USER404_1")
	void missingUser() {
		given(userQueryService.findPlanOverride(USER_ID))
			.willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

		assertThatThrownBy(() -> planQueryService.getCurrentPlan(USER_ID))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
	}

}
