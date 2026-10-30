package com.sniptext.domain.template.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.sniptext.domain.template.service.dto.result.TemplateCreateResult;

/**
 * 새 템플릿 저장 결과 (명세 10번). 목록·상세·수정은 화면마다 바뀌는 방향이 달라 각자 응답 객체를 둔다.
 * 시각은 전역 직렬화 설정이 +09:00을 붙여 내보낸다.
 */
public record TemplateCreateResponse(
	Long templateId,
	String title,
	String shortcut,
	String content,
	List<String> variables,
	LocalDateTime lastUsedAt,
	LocalDateTime createdAt,
	LocalDateTime editedAt
) {

	public static TemplateCreateResponse from(TemplateCreateResult result) {
		return new TemplateCreateResponse(
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
