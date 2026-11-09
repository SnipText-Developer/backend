package com.sniptext.domain.template.controller.dto;

import com.sniptext.domain.template.service.dto.result.TrashPurgeResult;

/** 휴지통 템플릿 개별 영구 삭제 결과 (명세 17번) */
public record TrashPurgeResponse(Long templateId) {

	public static TrashPurgeResponse from(TrashPurgeResult result) {
		return new TrashPurgeResponse(result.templateId());
	}

}
