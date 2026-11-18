package com.sniptext.domain.template.controller.dto;

import com.sniptext.domain.template.service.dto.result.TemplateShortcutAssignResult;

/** 휴지통 복구 결과 (명세 16번). shortcutChanged면 화면이 자동 번호 안내 토스트를 띄운다 */
public record TrashRestoreResponse(
	Long templateId,
	String title,
	String shortcut,
	String originalShortcut,
	boolean shortcutChanged
) {

	public static TrashRestoreResponse from(TemplateShortcutAssignResult result) {
		return new TrashRestoreResponse(
			result.templateId(),
			result.title(),
			result.shortcut(),
			result.originalShortcut(),
			result.shortcutChanged()
		);
	}

}
