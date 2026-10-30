package com.sniptext.domain.template.service.dto.command;

/** 새 템플릿 저장 입력. 값은 요청 단계에서 형식 검증을 마친 입력 그대로이고, 정규화는 값 객체가 한다. */
public record TemplateCreateCommand(
	Long userId,
	String title,
	String shortcut,
	String content
) {
}
