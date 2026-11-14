package com.sniptext.domain.gallery.service.dto.result;

import com.sniptext.domain.template.service.dto.result.TemplateShortcutAssignResult;

/** 내 템플릿으로 저장한 결과. 컨트롤러가 template의 결과 객체에 묶이지 않도록 gallery 쪽으로 옮겨 담는다. */
public record GallerySaveResult(
	Long templateId,
	String title,
	String shortcut,
	String originalShortcut,
	boolean shortcutChanged
) {

	public static GallerySaveResult from(TemplateShortcutAssignResult result) {
		return new GallerySaveResult(
			result.templateId(),
			result.title(),
			result.shortcut(),
			result.originalShortcut(),
			result.shortcutChanged()
		);
	}

}
