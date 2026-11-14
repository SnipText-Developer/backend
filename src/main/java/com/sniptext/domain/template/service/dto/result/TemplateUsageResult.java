package com.sniptext.domain.template.service.dto.result;

import java.time.LocalDateTime;

import com.sniptext.domain.template.entity.Template;

/** 사용 기록을 남긴 템플릿과 갱신된 최근 사용 시각. */
public record TemplateUsageResult(
	Long templateId,
	LocalDateTime lastUsedAt
) {

	public static TemplateUsageResult from(Template template) {
		return new TemplateUsageResult(template.getId(), template.getLastUsedAt());
	}

}
