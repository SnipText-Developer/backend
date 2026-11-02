package com.sniptext.domain.template.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.sniptext.domain.template.service.dto.result.TemplateUpdateResult;

/** 템플릿 수정 결과 (명세 11번). 시각은 전역 직렬화 설정이 +09:00을 붙여 내보낸다. */
public record TemplateUpdateResponse(
	Long templateId,
	String title,
	String shortcut,
	String content,
	List<String> variables,
	LocalDateTime lastUsedAt,
	LocalDateTime createdAt,
	LocalDateTime editedAt
) {

	public static TemplateUpdateResponse from(TemplateUpdateResult result) {
		return new TemplateUpdateResponse(
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
