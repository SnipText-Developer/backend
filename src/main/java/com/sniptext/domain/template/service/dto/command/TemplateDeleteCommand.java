package com.sniptext.domain.template.service.dto.command;

/** 템플릿 삭제(휴지통 이동) 입력. */
public record TemplateDeleteCommand(
	Long userId,
	Long templateId
) {
}
