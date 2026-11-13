package com.sniptext.domain.user.service.dto.result;

import java.time.LocalDateTime;

import com.sniptext.domain.ai.service.dto.result.AiToneUsageResult;

/** 사용량. 템플릿은 휴지통 제외 개수 기준이고, AI 톤 교정은 오늘(KST) 기준이다. */
public record UserUsageResult(TemplateUsage template, AiToneUsage aiTone) {

	public static UserUsageResult of(long templateCount, int templateLimit, AiToneUsageResult aiTone) {
		return new UserUsageResult(
			new TemplateUsage(templateCount, templateLimit, Math.max(templateLimit - templateCount, 0),
				templateCount >= templateLimit),
			new AiToneUsage(aiTone.usedCount(), aiTone.limit(), aiTone.remaining(), aiTone.resetAt()));
	}

	public record TemplateUsage(long count, int limit, long remaining, boolean full) {
	}

	public record AiToneUsage(int usedCount, int limit, int remaining, LocalDateTime resetAt) {
	}

}
