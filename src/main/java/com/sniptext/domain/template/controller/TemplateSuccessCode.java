package com.sniptext.domain.template.controller;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TemplateSuccessCode implements ResultCode {

	TEMPLATE_FOUND(HttpStatus.OK, "TEMPLATE200_2", "템플릿 조회에 성공했습니다."),
	TEMPLATE_UPDATED(HttpStatus.OK, "TEMPLATE200_3", "수정 내용이 저장되었습니다."),
	TEMPLATE_CREATED(HttpStatus.CREATED, "TEMPLATE201_1", "템플릿이 저장되었습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
