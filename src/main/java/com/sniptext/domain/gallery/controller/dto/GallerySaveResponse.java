package com.sniptext.domain.gallery.controller.dto;

import com.sniptext.domain.gallery.service.dto.result.GallerySaveResult;

/** 명세 21번 저장 결과. 개인 데이터라 캐시하지 않는다. */
public record GallerySaveResponse(
	Long templateId,
	String title,
	String shortcut,
	String originalShortcut,
	boolean shortcutChanged
) {

	public static GallerySaveResponse from(GallerySaveResult result) {
		return new GallerySaveResponse(
			result.templateId(),
			result.title(),
			result.shortcut(),
			result.originalShortcut(),
			result.shortcutChanged()
		);
	}

}
