package com.sniptext.domain.template.service.dto.result;

import java.time.LocalDateTime;

/** 확장 동기화. 바뀐 게 없으면 목록(templates)이 null이다 */
public record TemplateSyncResult(
	boolean changed,
	String version,
	LocalDateTime syncedAt,
	TemplateListResult templates
) {

	public static TemplateSyncResult unchanged(String version, LocalDateTime syncedAt) {
		return new TemplateSyncResult(false, version, syncedAt, null);
	}

	public static TemplateSyncResult of(String version, LocalDateTime syncedAt, TemplateListResult templates) {
		return new TemplateSyncResult(true, version, syncedAt, templates);
	}

}
