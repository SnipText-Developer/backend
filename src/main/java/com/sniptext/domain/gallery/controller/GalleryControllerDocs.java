package com.sniptext.domain.gallery.controller;

import org.springframework.http.ResponseEntity;

import com.sniptext.domain.gallery.controller.dto.GallerySaveResponse;
import com.sniptext.domain.gallery.controller.dto.GalleryTemplateListResponse;
import com.sniptext.domain.gallery.controller.dto.GalleryTemplateResponse;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 갤러리 API의 스웨거 설명. 매핑은 {@link GalleryController}에 있다.
 * 자세한 규칙은 docs/s1-api-spec.md 19~21번이 기준이다.
 */
@Tag(name = "갤러리", description = "갤러리 템플릿 조회와 내 템플릿으로 저장")
public interface GalleryControllerDocs {

	// 로그인이 필요 없는 공개 조회라 빈 @SecurityRequirements로 전역 Bearer 요구(자물쇠)를 지운다.
	@Operation(summary = "갤러리 템플릿 전체 조회",
		description = "로그인 없이 호출합니다. 페이지 나눔 없이 노출 순서대로 전체를 내려주며, 누구에게나 같은 응답이라 5분간 공개 캐시됩니다.")
	@SecurityRequirements
	ResponseEntity<ApiResponse<GalleryTemplateListResponse>> getGalleryTemplates();

	@Operation(summary = "갤러리 템플릿 상세 조회",
		description = "로그인 없이 호출합니다. 미리보기 패널은 목록 데이터로 그려도 됩니다.")
	@SecurityRequirements
	ResponseEntity<ApiResponse<GalleryTemplateResponse>> getGalleryTemplate(Long galleryTemplateId);

	// 실제 응답이 201이라 기본값(200) 대신 201로 표시한다. 이름이 같은 공통 응답 ApiResponse와 겹쳐 전체 이름으로 쓴다.
	@Operation(summary = "갤러리 템플릿을 내 템플릿으로 저장",
		description = "같은 템플릿을 여러 번 저장할 수 있습니다. 단축어가 겹치면 원본 뒤에 2~99를 붙이고 shortcutChanged가 true입니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "저장 성공")
	ResponseEntity<ApiResponse<GallerySaveResponse>> saveToMyTemplates(@LoginUser Long userId, Long galleryTemplateId);

}
