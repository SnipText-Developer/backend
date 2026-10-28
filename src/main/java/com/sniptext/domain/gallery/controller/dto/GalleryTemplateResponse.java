package com.sniptext.domain.gallery.controller.dto;

import java.util.List;

import com.sniptext.domain.gallery.entity.GalleryCategory;
import com.sniptext.domain.gallery.service.dto.result.GalleryTemplateResult;

/** 갤러리 템플릿 하나 (명세 19번 목록의 항목, 20번 상세). 공개 캐시 대상이라 회원별 값을 넣지 않는다. */
public record GalleryTemplateResponse(
	Long galleryTemplateId,
	GalleryCategory category,
	String categoryName,
	String title,
	String shortcut,
	String content,
	List<String> variables
) {

	public static GalleryTemplateResponse from(GalleryTemplateResult result) {
		return new GalleryTemplateResponse(
			result.galleryTemplateId(),
			result.category(),
			result.category().getDisplayName(),
			result.title(),
			result.shortcut(),
			result.content(),
			result.variables()
		);
	}

}
