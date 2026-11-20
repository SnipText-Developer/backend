package com.sniptext.domain.user.service.dto.result;

import java.time.LocalDateTime;

import com.sniptext.domain.ai.service.dto.result.AiToneUsageResult;
import com.sniptext.domain.template.support.TemplateLimit;
import com.sniptext.global.policy.UsageLimits;

/**
 * 사용량. 템플릿은 휴지통 제외 개수 기준이고, AI 톤 교정은 오늘(KST) 기준이다.
 * 남은 개수와 한도 도달은 저장 차단과 같은 규칙(TemplateLimit)으로 계산한다.
 */
public record UserUsageResult(TemplateUsage template, AiToneUsage aiTone) {

	public static UserUsageResult of(long templateCount, AiToneUsageResult aiTone) {
		return new UserUsageResult(
			new TemplateUsage(Math.toIntExact(templateCount), UsageLimits.TEMPLATE_LIMIT,
				TemplateLimit.remaining(templateCount), TemplateLimit.isReached(templateCount)),
			new AiToneUsage(aiTone.usedCount(), aiTone.limit(), aiTone.remaining(), aiTone.resetAt()));
	}

	public record TemplateUsage(int count, int limit, int remaining, boolean full) {
	}

	public record AiToneUsage(int usedCount, int limit, int remaining, LocalDateTime resetAt) {
	}

}
