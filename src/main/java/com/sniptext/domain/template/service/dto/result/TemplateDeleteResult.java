package com.sniptext.domain.template.service.dto.result;

import java.time.LocalDateTime;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.support.TrashRetention;

/** 휴지통으로 옮긴 템플릿. 이름은 삭제 토스트용이다. */
public record TemplateDeleteResult(
	Long templateId,
	String title,
	LocalDateTime deletedAt,
	LocalDateTime purgeAt
) {

	public static TemplateDeleteResult from(Template template) {
		return new TemplateDeleteResult(
			template.getId(),
			template.getTitle().getValue(),
			template.getDeletedAt(),
			TrashRetention.purgeAt(template.getDeletedAt())
		);
	}

}
