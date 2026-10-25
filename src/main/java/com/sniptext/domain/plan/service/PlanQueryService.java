package com.sniptext.domain.plan.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.plan.service.dto.result.PlanResult;
import com.sniptext.domain.user.entity.Plan;
import com.sniptext.domain.user.service.UserQueryService;

import lombok.RequiredArgsConstructor;

/**
 * 현재 플랜 판정과 플랜별 한도는 이 클래스 한 곳에서만 정한다. 다른 곳에서 플랜이나 한도 숫자를 직접 쓰지 않는다.
 * 결제 도입 전에는 회원의 plan_override 값, 없으면 FREE. 결제가 들어오면 판정 안쪽만 구독 기준으로 바꾼다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanQueryService {

	private static final int FREE_TEMPLATE_LIMIT = 5;
	private static final int FREE_AI_TONE_DAILY_LIMIT = 0;
	private static final int PRO_TEMPLATE_LIMIT = 50;
	private static final int PRO_AI_TONE_DAILY_LIMIT = 10;

	private final UserQueryService userQueryService;

	/** 없는 회원, 탈퇴 회원이면 USER404_1. */
	public PlanResult getCurrentPlan(Long userId) {
		Plan plan = userQueryService.findPlanOverride(userId).orElse(Plan.FREE);
		return switch (plan) {
			case FREE -> new PlanResult(plan, FREE_TEMPLATE_LIMIT, FREE_AI_TONE_DAILY_LIMIT);
			case PRO -> new PlanResult(plan, PRO_TEMPLATE_LIMIT, PRO_AI_TONE_DAILY_LIMIT);
		};
	}

}
