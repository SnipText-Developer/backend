package com.sniptext.domain.template.controller;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrashSuccessCode implements ResultCode {

	TRASH_LIST_FOUND(HttpStatus.OK, "TRASH200_1", "휴지통 조회에 성공했습니다."),
	TRASH_RESTORED(HttpStatus.OK, "TRASH200_2", "템플릿을 복구했습니다."),
	TRASH_PURGED(HttpStatus.OK, "TRASH200_3", "템플릿을 영구 삭제했습니다."),
	TRASH_EMPTIED(HttpStatus.OK, "TRASH200_4", "휴지통을 비웠습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
