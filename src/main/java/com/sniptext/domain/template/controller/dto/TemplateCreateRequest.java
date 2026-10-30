package com.sniptext.domain.template.controller.dto;

import com.sniptext.domain.template.annotation.ValidShortcut;
import com.sniptext.domain.template.annotation.ValidTemplateContent;
import com.sniptext.domain.template.annotation.ValidTemplateTitle;
import com.sniptext.domain.template.service.dto.command.TemplateCreateCommand;

/**
 * 새 템플릿 저장 (명세 10번). 여러 개가 틀리면 선언 순서상 첫 에러 하나만 내려주므로 이름 → 단축어 → 내용 순서를 지킨다.
 * 공백·길이 기준이 화면과 같도록 @NotBlank·@Size 대신 값 객체 규칙을 쓰는 검증만 붙인다.
 */
public record TemplateCreateRequest(
	@ValidTemplateTitle String title,
	@ValidShortcut String shortcut,
	@ValidTemplateContent String content
) {

	public TemplateCreateCommand toCommand(Long userId) {
		return new TemplateCreateCommand(userId, title, shortcut, content);
	}

}
