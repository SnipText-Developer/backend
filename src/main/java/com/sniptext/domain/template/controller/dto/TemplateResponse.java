package com.sniptext.domain.template.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.sniptext.domain.template.service.dto.result.TemplateCreateResult;

/**
 * 내 템플릿 하나. 명세 8번 목록의 항목, 9번 상세, 10번 저장, 11번 수정의 응답이 같은 모양이다.
 * 시각은 전역 직렬화 설정이 +09:00을 붙여 내보낸다.
 */
public record TemplateResponse(
	Long templateId,
	String title,
	String shortcut,
	String content,
	List<String> variables,
	LocalDateTime lastUsedAt,
	LocalDateTime createdAt,
	LocalDateTime editedAt
) {

	public static TemplateResponse from(TemplateCreateResult result) {
		return new TemplateResponse(
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
