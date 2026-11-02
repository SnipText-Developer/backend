package com.sniptext.domain.template.controller;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrashSuccessCode implements ResultCode {

	TRASH_LIST_FOUND(HttpStatus.OK, "TRASH200_1", "휴지통 조회에 성공했습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
