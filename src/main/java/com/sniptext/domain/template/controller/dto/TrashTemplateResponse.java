package com.sniptext.domain.template.controller.dto;

import java.time.LocalDateTime;

import com.sniptext.domain.template.service.dto.result.TrashTemplateResult;

/** 명세 15번 휴지통 항목. 시각은 전역 직렬화 설정이 +09:00을 붙여 내보낸다 */
public record TrashTemplateResponse(
	Long templateId,
	String title,
	String shortcut,
	LocalDateTime deletedAt,
	LocalDateTime purgeAt
) {

	public static TrashTemplateResponse from(TrashTemplateResult result) {
		return new TrashTemplateResponse(
			result.templateId(),
			result.title(),
			result.shortcut(),
			result.deletedAt(),
			result.purgeAt()
		);
	}

}
