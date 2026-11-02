package com.sniptext.domain.template.service.dto.result;

import java.time.LocalDateTime;

import com.sniptext.domain.template.repository.dto.TrashTemplateRow;
import com.sniptext.domain.template.support.TrashRetention;

/** 휴지통 항목. 본문은 화면에 보이지 않아 내보내지 않는다 */
public record TrashTemplateResult(
	Long templateId,
	String title,
	String shortcut,
	LocalDateTime deletedAt,
	LocalDateTime purgeAt
) {

	public static TrashTemplateResult from(TrashTemplateRow row) {
		return new TrashTemplateResult(
			row.id(),
			row.title(),
			row.shortcut(),
			row.deletedAt(),
			TrashRetention.purgeAt(row.deletedAt())
		);
	}

}
