package com.sniptext.domain.template.service.dto.query;

/** 확장 동기화. version은 확장이 갖고 있는 목록의 버전이고, 처음이면 null이다 */
public record TemplateSyncQuery(
	Long userId,
	String version
) {
}
