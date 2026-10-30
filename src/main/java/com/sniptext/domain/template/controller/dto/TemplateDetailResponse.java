package com.sniptext.domain.template.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.sniptext.domain.template.service.dto.result.TemplateDetailResult;

/** 내 템플릿 상세 (명세 9번). 시각은 전역 직렬화 설정이 +09:00을 붙여 내보낸다. */
public record TemplateDetailResponse(
	Long templateId,
	String title,
	String shortcut,
	String content,
	List<String> variables,
	LocalDateTime lastUsedAt,
	LocalDateTime createdAt,
	LocalDateTime editedAt
) {

	public static TemplateDetailResponse from(TemplateDetailResult result) {
		return new TemplateDetailResponse(
			result.templateId(),
			result.title(),
			result.shortcut(),
			result.content(),
			result.variables(),
			result.lastUsedAt(),
			result.createdAt(),
			result.editedAt()
		);
	}

}
