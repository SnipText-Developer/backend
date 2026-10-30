package com.sniptext.domain.template.controller;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TemplateSuccessCode implements ResultCode {

	TEMPLATE_CREATED(HttpStatus.CREATED, "TEMPLATE201_1", "템플릿이 저장되었습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
