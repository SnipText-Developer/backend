package com.sniptext.domain.template.controller.dto;

import java.util.List;

import com.sniptext.domain.template.service.dto.result.TrashListResult;

/** 명세 15번 휴지통 목록 */
public record TrashListResponse(long totalCount, List<TrashTemplateResponse> templates, boolean hasNext,
	String nextCursor) {

	public static TrashListResponse from(TrashListResult result) {
		return new TrashListResponse(
			result.totalCount(),
			result.templates().stream().map(TrashTemplateResponse::from).toList(),
			result.hasNext(),
			result.nextCursor()
		);
	}

}
