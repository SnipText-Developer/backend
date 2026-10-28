package com.sniptext.domain.gallery.controller;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GallerySuccessCode implements ResultCode {

	GALLERY_TEMPLATES_FOUND(HttpStatus.OK, "GALLERY200_2", "갤러리 조회에 성공했습니다."),
	GALLERY_TEMPLATE_FOUND(HttpStatus.OK, "GALLERY200_3", "갤러리 템플릿 조회에 성공했습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
