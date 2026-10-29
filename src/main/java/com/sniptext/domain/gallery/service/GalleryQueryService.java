package com.sniptext.domain.gallery.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.gallery.exception.GalleryErrorCode;
import com.sniptext.domain.gallery.repository.GalleryTemplateRepository;
import com.sniptext.domain.gallery.service.dto.result.GalleryTemplateListResult;
import com.sniptext.domain.gallery.service.dto.result.GalleryTemplateResult;
import com.sniptext.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/** 갤러리 조회. 공개 캐시 대상이라 누구에게나 같은 결과를 돌려준다 (회원별 값을 넣지 않는다). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GalleryQueryService {

	private final GalleryTemplateRepository galleryTemplateRepository;

	/** 공개 템플릿 전체를 노출 순서대로. 100개 이하라 페이지를 나누지 않는다 */
	public GalleryTemplateListResult getGalleryTemplates() {
		List<GalleryTemplateResult> templates = galleryTemplateRepository.findAllByPublishedTrueOrderByDisplayOrderAscIdAsc()
			.stream()
			.map(GalleryTemplateResult::from)
			.toList();
		return GalleryTemplateListResult.from(templates);
	}

	/** 없거나 비공개면 GALLERY404_1 */
	public GalleryTemplateResult getGalleryTemplate(Long galleryTemplateId) {
		return galleryTemplateRepository.findByIdAndPublishedTrue(galleryTemplateId)
			.map(GalleryTemplateResult::from)
			.orElseThrow(() -> new BusinessException(GalleryErrorCode.GALLERY_TEMPLATE_NOT_FOUND));
	}

}
