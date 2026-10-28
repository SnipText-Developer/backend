package com.sniptext.domain.gallery.exception;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GalleryErrorCode implements ResultCode {

	/** 없거나 비공개 처리된 갤러리 템플릿 */
	GALLERY_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "GALLERY404_1", "갤러리 템플릿을 찾을 수 없어요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
