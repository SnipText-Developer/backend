package com.sniptext.domain.template.service.dto.result;

import java.time.LocalDateTime;
import java.util.List;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.support.TemplateVariableExtractor;

/** 저장한 템플릿. 새 템플릿 저장과 갤러리에서 저장(createFromGallery)이 같이 쓴다. 변수 목록은 저장하지 않고 본문에서 뽑는다. */
public record TemplateCreateResult(
	Long templateId,
	String title,
	String shortcut,
	String content,
	List<String> variables,
	LocalDateTime lastUsedAt,
	LocalDateTime createdAt,
	LocalDateTime editedAt
) {

	public static TemplateCreateResult from(Template template) {
		String content = template.getContent().getValue();
		return new TemplateCreateResult(
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
