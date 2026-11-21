package com.sniptext.domain.template.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

import com.sniptext.domain.template.controller.dto.TrashEmptyResponse;
import com.sniptext.domain.template.controller.dto.TrashListRequest;
import com.sniptext.domain.template.controller.dto.TrashListResponse;
import com.sniptext.domain.template.controller.dto.TrashPurgeResponse;
import com.sniptext.domain.template.controller.dto.TrashRestoreResponse;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 휴지통 API의 스웨거 설명. 매핑·검증은 {@link TrashController}에 있다.
 * 자세한 규칙은 docs/s1-api-spec.md 15~18번이 기준이다.
 */
@Tag(name = "휴지통", description = "삭제된 템플릿 조회·복구·영구 삭제")
public interface TrashControllerDocs {

	@Operation(summary = "삭제된 템플릿 목록 조회",
		description = "최근 삭제순으로, 무한 스크롤용 커서 방식으로 나눠 내려줍니다.")
	ResponseEntity<ApiResponse<TrashListResponse>> getTrash(
		@LoginUser Long userId,
		@ParameterObject TrashListRequest request);

	@Operation(summary = "휴지통 템플릿 복구",
		description = "내 템플릿으로 복원해 개수가 1 늘어납니다. 단축어가 겹치면 원본 뒤에 2~99를 붙입니다.")
	ResponseEntity<ApiResponse<TrashRestoreResponse>> restore(@LoginUser Long userId, Long templateId);

	@Operation(summary = "휴지통 템플릿 개별 영구 삭제",
		description = "휴지통에 있는 템플릿만 삭제되며 복구할 수 없습니다.")
	ResponseEntity<ApiResponse<TrashPurgeResponse>> purge(@LoginUser Long userId, Long templateId);

	@Operation(summary = "휴지통 비우기",
		description = "휴지통 전체를 영구 삭제하며 복구할 수 없습니다.")
	ResponseEntity<ApiResponse<TrashEmptyResponse>> empty(@LoginUser Long userId);

}
