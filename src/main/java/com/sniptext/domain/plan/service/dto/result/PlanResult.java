package com.sniptext.domain.plan.service.dto.result;

import com.sniptext.domain.user.entity.Plan;

/**
 * 현재 플랜과 그 플랜의 한도.
 *
 * @param templateLimit    보유 템플릿 한도 (휴지통 제외 개수 기준)
 * @param aiToneDailyLimit AI 톤 교정 하루 한도 (0이면 쓸 수 없음)
 */
public record PlanResult(Plan plan, int templateLimit, int aiToneDailyLimit) {
}
