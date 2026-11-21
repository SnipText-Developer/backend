package com.sniptext.domain.template.controller.dto;

import com.sniptext.domain.template.service.dto.query.TrashListQuery;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 휴지통 목록 쿼리 파라미터 (명세 15번).
 * 컨트롤러 메서드 파라미터에 검증을 직접 붙이면 Docs 인터페이스 구현 때문에 하이버네이트 검증기가 모든 요청을 500으로 만들어(HV000151) 객체로 묶는다.
 * 숫자가 아닌 size는 바인딩에서 COMMON400_1, 범위 밖이면 TRASH400_1. size가 없거나 비어 있으면 기본값을 쓴다.
 */
public record TrashListRequest(
	@Parameter(description = "다음 페이지 커서. 첫 요청은 비움")
	String cursor,

	@Parameter(description = "한 번에 받을 개수. 기본 20, 최대 50", schema = @Schema(implementation = Integer.class, defaultValue = "20"))
	@Min(value = 1, message = "TRASH400_1") @Max(value = 50, message = "TRASH400_1")
	Integer size
) {

	private static final int DEFAULT_SIZE = 20;

	public TrashListQuery toQuery(Long userId) {
		return new TrashListQuery(userId, cursor, size == null ? DEFAULT_SIZE : size);
	}

}
