package com.sniptext.domain.template.service.dto.result;

import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.Template;

/** 자동 번호 규칙으로 단축어를 정해 저장한 템플릿. 갤러리 저장과 휴지통 복구가 같이 쓴다. */
public record TemplateShortcutAssignResult(
	Long templateId,
	String title,
	String shortcut,
	String originalShortcut,
	boolean shortcutChanged
) {

	public static TemplateShortcutAssignResult of(Template template, Shortcut original) {
		return new TemplateShortcutAssignResult(
			template.getId(),
			template.getTitle().getValue(),
			template.getShortcut().getValue(),
			original.getValue(),
			template.getShortcut().isChangedFrom(original)
		);
	}

}
