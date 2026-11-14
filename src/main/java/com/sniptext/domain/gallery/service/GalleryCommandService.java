package com.sniptext.domain.gallery.service;

import org.springframework.stereotype.Service;

import com.sniptext.domain.gallery.service.dto.command.GallerySaveCommand;
import com.sniptext.domain.gallery.service.dto.result.GallerySaveResult;
import com.sniptext.domain.gallery.service.dto.result.GalleryTemplateResult;
import com.sniptext.domain.template.service.TemplateCommandService;
import com.sniptext.domain.template.service.dto.command.TemplateCreateFromGalleryCommand;

import lombok.RequiredArgsConstructor;

/**
 * 갤러리 템플릿을 내 템플릿으로 저장.
 * 서비스 클래스에 readOnly 트랜잭션을 거는 규칙의 예외로, 클래스·메서드 어디에도 @Transactional을 붙이지 않는다.
 * createFromGallery는 회원 잠금이 트랜잭션의 첫 조회여야 해서 바깥 트랜잭션이 있으면 예외를 던진다.
 * 원본 조회는 자체 조회 트랜잭션에서 끝내고, 그 뒤에 저장을 부른다.
 */
@Service
@RequiredArgsConstructor
public class GalleryCommandService {

	private final GalleryQueryService galleryQueryService;

	private final TemplateCommandService templateCommandService;

	/** 없거나 비공개면 GALLERY404_1. 한도·자동 번호 에러는 createFromGallery가 던진다 */
	public GallerySaveResult saveToMyTemplates(GallerySaveCommand command) {
		GalleryTemplateResult original = galleryQueryService.getGalleryTemplate(command.galleryTemplateId());

		return GallerySaveResult.from(templateCommandService.createFromGallery(new TemplateCreateFromGalleryCommand(
			command.userId(),
			command.galleryTemplateId(),
			original.title(),
			original.shortcut(),
			original.content())));
	}

}
