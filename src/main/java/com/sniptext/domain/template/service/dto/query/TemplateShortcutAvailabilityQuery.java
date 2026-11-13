package com.sniptext.domain.template.service.dto.query;

/** 단축어 확인 입력. excludeTemplateId는 편집 중인 자기 자신이고, 없으면 null이다. */
public record TemplateShortcutAvailabilityQuery(
	Long userId,
	String shortcut,
	Long excludeTemplateId
) {
}
