package com.sniptext.domain.template.controller.dto;

import java.time.LocalDateTime;

import com.sniptext.domain.template.service.dto.result.TemplateUsageResult;

/** 템플릿 사용 기록 결과 (명세 14번). 시각은 전역 직렬화 설정이 +09:00을 붙여 내보낸다. */
public record TemplateUsageResponse(
	Long templateId,
	LocalDateTime lastUsedAt
) {

	public static TemplateUsageResponse from(TemplateUsageResult result) {
		return new TemplateUsageResponse(result.templateId(), result.lastUsedAt());
	}

}
