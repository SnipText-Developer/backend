package com.sniptext.domain.template.service.dto.command;

/** 템플릿 사용 기록 입력. 채널은 요청 단계에서 검증만 하고 저장하지 않으므로 넘기지 않는다. */
public record TemplateUsageCommand(
	Long userId,
	Long templateId
) {
}
