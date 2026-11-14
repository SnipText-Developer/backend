package com.sniptext.domain.template.exception;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrashErrorCode implements ResultCode {

	/** 커서를 해석할 수 없거나 size가 1~50 밖 */
	INVALID_PAGE_REQUEST(HttpStatus.BAD_REQUEST, "TRASH400_1", "잘못된 페이지 요청이에요."),
	/** 휴지통에 없는 템플릿 (없는 ID, 다른 회원의 템플릿, 이미 복구·영구 삭제됨) */
	TRASHED_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "TRASH404_1", "휴지통에서 템플릿을 찾을 수 없어요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
