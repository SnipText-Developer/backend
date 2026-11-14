package com.sniptext.domain.template.service.dto.result;

import java.time.LocalDateTime;
import java.util.List;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.support.TemplateVariableExtractor;

/** 내 템플릿 목록의 한 항목. 변수 목록은 저장하지 않고 복호화한 본문에서 뽑는다. */
public record TemplateListItemResult(
	Long templateId,
	String title,
	String shortcut,
	String content,
	List<String> variables,
	LocalDateTime lastUsedAt,
	LocalDateTime createdAt,
	LocalDateTime editedAt
) {

	public static TemplateListItemResult from(Template template) {
		String content = template.getContent().getValue();
		return new TemplateListItemResult(
			template.getId(),
			template.getTitle().getValue(),
			template.getShortcut().getValue(),
			content,
			TemplateVariableExtractor.extract(content),
			template.getLastUsedAt(),
			template.getCreatedAt(),
			template.getEditedAt()
		);
	}

}
