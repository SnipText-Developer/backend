package com.sniptext.domain.template.service.dto.command;

/** 템플릿 수정 입력. 값은 요청 단계에서 형식 검증을 마친 입력 그대로이고, 정규화는 값 객체가 한다. */
public record TemplateUpdateCommand(
	Long userId,
	Long templateId,
	String title,
	String shortcut,
	String content
) {
}
