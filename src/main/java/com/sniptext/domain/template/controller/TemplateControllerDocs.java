package com.sniptext.domain.template.controller;

import org.springframework.http.ResponseEntity;

import com.sniptext.domain.template.controller.dto.TemplateCreateRequest;
import com.sniptext.domain.template.controller.dto.TemplateCreateResponse;
import com.sniptext.domain.template.controller.dto.TemplateDeleteResponse;
import com.sniptext.domain.template.controller.dto.TemplateDetailResponse;
import com.sniptext.domain.template.controller.dto.TemplateListResponse;
import com.sniptext.domain.template.controller.dto.TemplateShortcutAvailabilityResponse;
import com.sniptext.domain.template.controller.dto.TemplateSyncResponse;
import com.sniptext.domain.template.controller.dto.TemplateUpdateRequest;
import com.sniptext.domain.template.controller.dto.TemplateUpdateResponse;
import com.sniptext.domain.template.controller.dto.TemplateUsageRequest;
import com.sniptext.domain.template.controller.dto.TemplateUsageResponse;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 내 템플릿 API의 스웨거 설명. 매핑·검증은 {@link TemplateController}에 있다.
 * 자세한 규칙은 docs/s1-api-spec.md 8~14번, 확장 동기화는 docs/s2-api-spec.md 27번이 기준이다.
 */
@Tag(name = "내 템플릿", description = "내 템플릿 조회·저장·수정·삭제와 단축어 확인, 확장 동기화")
public interface TemplateControllerDocs {

	@Operation(summary = "내 템플릿 목록 조회",
		description = "휴지통을 뺀 전체를 페이지 나눔 없이 생성 순(오래된 것 먼저)으로 내려줍니다.")
	ResponseEntity<ApiResponse<TemplateListResponse>> getTemplates(@LoginUser Long userId);

	@Operation(summary = "내 템플릿 상세 조회",
		description = "편집 화면을 주소로 직접 열거나 새로고침했을 때 씁니다.")
	ResponseEntity<ApiResponse<TemplateDetailResponse>> getTemplate(@LoginUser Long userId, Long templateId);

	// 실제 응답이 201이라 기본값(200) 대신 201로 표시한다. 이름이 같은 공통 응답 ApiResponse와 겹쳐 전체 이름으로 쓴다.
	@Operation(summary = "새 템플릿 저장",
		description = "검증 순서는 이름 → 단축어 → 내용이고, 여러 개가 틀려도 첫 번째 에러 하나만 내려줍니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "저장 성공")
	ResponseEntity<ApiResponse<TemplateCreateResponse>> createTemplate(
		@LoginUser Long userId,
		TemplateCreateRequest request);

	@Operation(summary = "템플릿 수정",
		description = "세 필드를 모두 보내는 전체 수정입니다. 바뀌지 않은 필드도 그대로 보내주세요.")
	ResponseEntity<ApiResponse<TemplateUpdateResponse>> updateTemplate(
		@LoginUser Long userId,
		Long templateId,
		TemplateUpdateRequest request);

	@Operation(summary = "템플릿 삭제 → 휴지통 이동",
		description = "소프트 삭제입니다. 삭제 즉시 단축어 점유가 풀려 다른 템플릿이 같은 단축어를 쓸 수 있습니다.")
	ResponseEntity<ApiResponse<TemplateDeleteResponse>> deleteTemplate(@LoginUser Long userId, Long templateId);

	@Operation(summary = "단축어 중복·형식 실시간 확인",
		description = "입력 확인용이라 형식 오류·예약어·중복도 에러가 아닌 200 + reason으로 내려줍니다.")
	ResponseEntity<ApiResponse<TemplateShortcutAvailabilityResponse>> checkShortcutAvailability(
		@LoginUser Long userId,
		@Parameter(description = "확인할 단축어. \"/\" 포함, 주소에 넣을 때 인코딩") String shortcut,
		@Parameter(description = "편집 모드일 때 자기 자신 템플릿 ID (중복 판정에서 제외)") Long excludeTemplateId);

	@Operation(summary = "템플릿 사용 기록",
		description = "클립보드 복사·삽입이 성공한 뒤에만 호출합니다. 최근 사용 시각만 갱신하고 사용 횟수는 저장하지 않습니다.")
	ResponseEntity<ApiResponse<TemplateUsageResponse>> recordUsage(
		@LoginUser Long userId,
		Long templateId,
		TemplateUsageRequest request);

	@Operation(summary = "확장 템플릿 동기화",
		description = "확장이 가진 목록의 버전과 지금 버전을 비교해, 바뀐 게 있을 때만 휴지통을 뺀 전체 목록(필드·정렬은 목록 조회와 같음)을 내려줍니다. "
			+ "바뀐 게 없으면 totalCount·templates가 null입니다. version이 없거나 형식이 틀려도 에러 없이 전체를 내려줍니다.")
	ResponseEntity<ApiResponse<TemplateSyncResponse>> syncTemplates(
		@LoginUser Long userId,
		@Parameter(description = "확장이 갖고 있는 목록의 버전 (직전 응답의 version). 처음이면 비움. 화면에서 해석하지 않음") String version);

}
