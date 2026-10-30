package com.sniptext.domain.template.exception;

import org.springframework.http.HttpStatus;

import com.sniptext.domain.template.entity.ShortcutViolation;
import com.sniptext.domain.template.entity.TemplateContentViolation;
import com.sniptext.domain.template.entity.TemplateTitleViolation;
import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TemplateErrorCode implements ResultCode {

	/** 이름이 비었거나 공백만 입력 */
	TITLE_EMPTY(HttpStatus.BAD_REQUEST, "TEMPLATE400_1", "템플릿 이름을 입력해주세요."),
	/** 이름 50자 초과 */
	TITLE_TOO_LONG(HttpStatus.BAD_REQUEST, "TEMPLATE400_2", "템플릿 이름은 50자까지 입력할 수 있어요."),
	/** 단축어가 비었거나 "/"만 있음 */
	SHORTCUT_EMPTY(HttpStatus.BAD_REQUEST, "TEMPLATE400_3", "호출 단축어를 입력해주세요."),
	/** 단축어 형식 불일치 (공백·특수문자·이모지, 길이) */
	SHORTCUT_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "TEMPLATE400_4", "한글·영문·숫자 2~10자로 입력해주세요."),
	/** 예약어 사용 (/톤교정) */
	SHORTCUT_RESERVED(HttpStatus.BAD_REQUEST, "TEMPLATE400_5", "사용할 수 없는 단축어예요."),
	/** 본문이 비었거나 공백만 입력 */
	CONTENT_EMPTY(HttpStatus.BAD_REQUEST, "TEMPLATE400_6", "템플릿 내용을 입력해주세요."),
	/** 본문 3000자 초과 */
	CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "TEMPLATE400_7", "템플릿 내용은 3,000자까지 입력할 수 있어요."),
	/** 활성 템플릿 수가 플랜 한도 이상 (화면은 한도 모달) */
	TEMPLATE_LIMIT_REACHED(HttpStatus.FORBIDDEN, "TEMPLATE403_1", "템플릿 저장 한도에 도달했어요."),
	/** 내 템플릿(휴지통 제외)에 같은 단축어 키가 있음. 대소문자 무시 */
	SHORTCUT_DUPLICATED(HttpStatus.CONFLICT, "TEMPLATE409_1", "이미 사용 중인 단축어예요.");

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

	public static TemplateErrorCode from(TemplateTitleViolation violation) {
		return switch (violation) {
			case EMPTY -> TITLE_EMPTY;
			case TOO_LONG -> TITLE_TOO_LONG;
		};
	}

	public static TemplateErrorCode from(TemplateContentViolation violation) {
		return switch (violation) {
			case EMPTY -> CONTENT_EMPTY;
			case TOO_LONG -> CONTENT_TOO_LONG;
		};
	}

}
