package com.sniptext.domain.template.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.sniptext.domain.template.service.dto.result.TemplateSyncResult;

/**
 * S2 명세 27번 확장 템플릿 동기화. 바뀐 게 없으면 totalCount·templates가 null이다.
 * 목록 항목은 8번 목록과 같은 객체를 쓴다 (필드·정렬이 같아야 한다).
 */
public record TemplateSyncResponse(
	boolean changed,
	String version,
	LocalDateTime syncedAt,
	Integer totalCount,
	List<TemplateListItemResponse> templates
) {

	public static TemplateSyncResponse from(TemplateSyncResult result) {
		if (result.templates() == null) {
			return new TemplateSyncResponse(result.changed(), result.version(), result.syncedAt(), null, null);
		}
		TemplateListResponse list = TemplateListResponse.from(result.templates());
		return new TemplateSyncResponse(result.changed(), result.version(), result.syncedAt(), list.totalCount(),
			list.templates());
	}

}
