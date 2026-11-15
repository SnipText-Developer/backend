package com.sniptext.domain.template.controller.dto;

import com.sniptext.domain.template.service.dto.result.TrashEmptyResult;

/** 휴지통 비우기 결과 (명세 18번). 비어 있었으면 0 */
public record TrashEmptyResponse(int deletedCount) {

	public static TrashEmptyResponse from(TrashEmptyResult result) {
		return new TrashEmptyResponse(result.deletedCount());
	}

}
