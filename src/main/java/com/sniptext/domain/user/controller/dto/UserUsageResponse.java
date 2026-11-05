package com.sniptext.domain.user.controller.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sniptext.domain.user.service.dto.result.UserUsageResult;

/** 사용량 (명세 6번). 시각은 전역 직렬화 설정이 +09:00을 붙여 내보낸다. */
public record UserUsageResponse(TemplateUsage template, AiToneUsage aiTone) {

	public static UserUsageResponse from(UserUsageResult result) {
		UserUsageResult.TemplateUsage template = result.template();
		UserUsageResult.AiToneUsage aiTone = result.aiTone();
		return new UserUsageResponse(
			new TemplateUsage(template.count(), template.limit(), template.remaining(), template.full()),
			new AiToneUsage(aiTone.usedCount(), aiTone.limit(), aiTone.remaining(), aiTone.resetAt()));
	}

	public record TemplateUsage(long count, int limit, long remaining, @JsonProperty("isFull") boolean isFull) {
	}

	public record AiToneUsage(int usedCount, int limit, int remaining, LocalDateTime resetAt) {
	}

}
