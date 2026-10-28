package com.sniptext.domain.gallery.controller.dto;

import java.util.List;

import com.sniptext.domain.gallery.service.dto.result.GalleryTemplateListResult;

/** 명세 19번 갤러리 목록 */
public record GalleryTemplateListResponse(int totalCount, List<GalleryTemplateResponse> templates) {

	public static GalleryTemplateListResponse from(GalleryTemplateListResult result) {
		return new GalleryTemplateListResponse(
			result.totalCount(),
			result.templates().stream().map(GalleryTemplateResponse::from).toList()
		);
	}

}
