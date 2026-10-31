package com.sniptext.domain.template.service.dto.result;

import java.time.LocalDateTime;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.support.TrashRetention;

/** 휴지통 항목. 본문은 화면에 보이지 않아 내보내지 않는다 */
public record TrashTemplateResult(
	Long templateId,
	String title,
	String shortcut,
	LocalDateTime deletedAt,
	LocalDateTime purgeAt
) {

	public static TrashTemplateResult from(Template template) {
		return new TrashTemplateResult(
			template.getId(),
			template.getTitle().getValue(),
			template.getShortcut().getValue(),
			template.getDeletedAt(),
			TrashRetention.purgeAt(template.getDeletedAt())
		);
	}

}
