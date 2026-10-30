package com.sniptext.domain.template.service.dto.command;

/**
 * 갤러리 템플릿을 내 템플릿으로 저장하는 입력. 갤러리 원본은 gallery가 읽어서 넘기고(없으면 GALLERY404_1도 gallery가 처리한다),
 * 값은 원본 그대로이고 정규화는 값 객체가 한다.
 */
public record TemplateCreateFromGalleryCommand(
	Long userId,
	Long galleryTemplateId,
	String title,
	String shortcut,
	String content
) {
}
