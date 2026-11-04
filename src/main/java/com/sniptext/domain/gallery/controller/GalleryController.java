package com.sniptext.domain.gallery.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.domain.gallery.controller.dto.GallerySaveResponse;
import com.sniptext.domain.gallery.controller.dto.GalleryTemplateListResponse;
import com.sniptext.domain.gallery.controller.dto.GalleryTemplateResponse;
import com.sniptext.domain.gallery.service.GalleryCommandService;
import com.sniptext.domain.gallery.service.GalleryQueryService;
import com.sniptext.domain.gallery.service.dto.command.GallerySaveCommand;
import com.sniptext.domain.gallery.service.dto.result.GallerySaveResult;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

import lombok.RequiredArgsConstructor;

/**
 * 갤러리.
 * 조회(목록·상세)는 로그인 불필요, 공개 캐시 5분. 토큰이 있든 없든 같은 응답이어야 하므로 로그인 회원을 받지 않는다.
 * 내 템플릿으로 저장은 로그인 필요, 캐시 금지.
 */
@RestController
@RequestMapping("/api/v1/gallery/templates")
@RequiredArgsConstructor
public class GalleryController {

	private final GalleryQueryService galleryQueryService;
	private final GalleryCommandService galleryCommandService;

	@GetMapping
	public ResponseEntity<ApiResponse<GalleryTemplateListResponse>> getGalleryTemplates() {
		return ApiResponse.success(GallerySuccessCode.GALLERY_TEMPLATES_FOUND,
			GalleryTemplateListResponse.from(galleryQueryService.getGalleryTemplates()));
	}

	@GetMapping("/{galleryTemplateId}")
	public ResponseEntity<ApiResponse<GalleryTemplateResponse>> getGalleryTemplate(
		@PathVariable Long galleryTemplateId) {
		return ApiResponse.success(GallerySuccessCode.GALLERY_TEMPLATE_FOUND,
			GalleryTemplateResponse.from(galleryQueryService.getGalleryTemplate(galleryTemplateId)));
	}

	@PostMapping("/{galleryTemplateId}/save")
	public ResponseEntity<ApiResponse<GallerySaveResponse>> saveToMyTemplates(
		@LoginUser Long userId,
		@PathVariable Long galleryTemplateId) {

		GallerySaveResult result = galleryCommandService.saveToMyTemplates(
			new GallerySaveCommand(userId, galleryTemplateId));

		return ApiResponse.success(GallerySuccessCode.GALLERY_TEMPLATE_SAVED, GallerySaveResponse.from(result));
	}

}
