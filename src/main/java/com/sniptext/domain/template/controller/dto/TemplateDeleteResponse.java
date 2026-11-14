package com.sniptext.domain.template.controller.dto;

import java.time.LocalDateTime;

import com.sniptext.domain.template.service.dto.result.TemplateDeleteResult;

/** 템플릿 삭제(휴지통 이동) 결과 (명세 12번). 시각은 전역 직렬화 설정이 +09:00을 붙여 내보낸다. */
public record TemplateDeleteResponse(
	Long templateId,
	String title,
	LocalDateTime deletedAt,
	LocalDateTime purgeAt
) {

	public static TemplateDeleteResponse from(TemplateDeleteResult result) {
		return new TemplateDeleteResponse(result.templateId(), result.title(), result.deletedAt(), result.purgeAt());
	}

}
