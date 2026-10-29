package com.sniptext.domain.gallery.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 갤러리 카테고리 (명세 공통 규칙 6장). 전체 탭은 화면에서 처리한다. */
@Getter
@RequiredArgsConstructor
public enum GalleryCategory {

	WORK("업무"),
	SALES("영업"),
	CUSTOMER_SERVICE("고객 응대"),
	PERSONAL("개인");

	/** 카테고리 칩에 보이는 이름 */
	private final String displayName;

}
