package com.sniptext.domain.template.controller.dto;

import java.util.List;

import com.sniptext.domain.template.service.dto.result.TemplateListResult;

/** 명세 8번 내 템플릿 목록. 생성 순, 페이지 나눔 없음 */
public record TemplateListResponse(int totalCount, List<TemplateListItemResponse> templates) {

	public static TemplateListResponse from(TemplateListResult result) {
		return new TemplateListResponse(
			result.totalCount(),
			result.templates().stream().map(TemplateListItemResponse::from).toList()
		);
	}

}
