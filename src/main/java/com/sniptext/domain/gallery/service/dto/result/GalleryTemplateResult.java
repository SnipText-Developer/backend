package com.sniptext.domain.gallery.service.dto.result;

import java.util.List;

import com.sniptext.domain.gallery.entity.GalleryCategory;
import com.sniptext.domain.gallery.entity.GalleryTemplate;
import com.sniptext.domain.template.support.TemplateVariableExtractor;

/** 갤러리 템플릿 하나. 변수 목록은 저장하지 않고 본문에서 뽑는다. */
public record GalleryTemplateResult(
	Long galleryTemplateId,
	GalleryCategory category,
	String title,
	String shortcut,
	String content,
	List<String> variables
) {

	public static GalleryTemplateResult from(GalleryTemplate galleryTemplate) {
		return new GalleryTemplateResult(
			galleryTemplate.getId(),
			galleryTemplate.getCategory(),
			galleryTemplate.getTitle(),
			galleryTemplate.getShortcut(),
			galleryTemplate.getContent(),
			TemplateVariableExtractor.extract(galleryTemplate.getContent())
		);
	}

}
