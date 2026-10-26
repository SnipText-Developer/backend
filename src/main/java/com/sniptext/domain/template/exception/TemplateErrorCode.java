package com.sniptext.domain.template.exception;

import org.springframework.http.HttpStatus;

import com.sniptext.domain.template.entity.ShortcutViolation;
import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TemplateErrorCode implements ResultCode {

	/** 단축어가 비었거나 "/"만 있음 */
	SHORTCUT_EMPTY(HttpStatus.BAD_REQUEST, "TEMPLATE400_3", "호출 단축어를 입력해주세요."),
	/** 단축어 형식 불일치 (공백·특수문자·이모지, 길이) */
	SHORTCUT_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "TEMPLATE400_4", "한글·영문·숫자 2~10자로 입력해주세요."),
	/** 예약어 사용 (/톤교정) */
	SHORTCUT_RESERVED(HttpStatus.BAD_REQUEST, "TEMPLATE400_5", "사용할 수 없는 단축어예요.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	public static TemplateErrorCode from(ShortcutViolation violation) {
		return switch (violation) {
			case EMPTY -> SHORTCUT_EMPTY;
			case INVALID_FORMAT -> SHORTCUT_INVALID_FORMAT;
			case RESERVED -> SHORTCUT_RESERVED;
		};
	}

}
