package com.sniptext.domain.template.controller.dto;

import com.sniptext.domain.template.annotation.ValidShortcut;
import com.sniptext.domain.template.annotation.ValidTemplateContent;
import com.sniptext.domain.template.annotation.ValidTemplateTitle;
import com.sniptext.domain.template.service.dto.command.TemplateUpdateCommand;

/**
 * 템플릿 수정 (명세 11번). 세 필드를 모두 보내는 전체 수정이다.
 * 여러 개가 틀리면 선언 순서상 첫 에러 하나만 내려주므로 이름 → 단축어 → 내용 순서를 지킨다.
 * 공백·길이 기준이 화면과 같도록 @NotBlank·@Size 대신 값 객체 규칙을 쓰는 검증만 붙인다.
 */
public record TemplateUpdateRequest(
	@ValidTemplateTitle String title,
	@ValidShortcut String shortcut,
	@ValidTemplateContent String content
) {

	public TemplateUpdateCommand toCommand(Long userId, Long templateId) {
		return new TemplateUpdateCommand(userId, templateId, title, shortcut, content);
	}

}
