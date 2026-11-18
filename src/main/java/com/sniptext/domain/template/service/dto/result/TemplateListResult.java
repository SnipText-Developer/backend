package com.sniptext.domain.template.service.dto.result;

import java.util.List;

/** 내 템플릿 목록. 페이지를 나누지 않으므로 개수는 항목 수와 같다. */
public record TemplateListResult(int totalCount, List<TemplateListItemResult> templates) {

	public static TemplateListResult from(List<TemplateListItemResult> templates) {
		return new TemplateListResult(templates.size(), List.copyOf(templates));
	}

}
