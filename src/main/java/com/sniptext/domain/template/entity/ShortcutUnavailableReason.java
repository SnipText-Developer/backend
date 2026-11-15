package com.sniptext.domain.template.entity;

/**
 * 단축어 확인 API(명세 13번)의 사용 불가 이유. 상수 이름이 API 값이다 (저장하지 않는 값).
 * 확인 API에는 빈 값 사유가 따로 없어 빈 값도 INVALID_FORMAT으로 내려준다. 빈 값 안내는 저장 시점의 TEMPLATE400_3이 한다.
 */
public enum ShortcutUnavailableReason {

	/** 비었거나 "/"만 있거나, "/" 뒤 한글(완성형·자모)·영문·숫자 2~10자가 아님 */
	INVALID_FORMAT,
	/** 예약어 (/톤교정) */
	RESERVED,
	/** 내 활성 템플릿(휴지통 제외)이 같은 비교 키를 씀 */
	DUPLICATED;

	public static ShortcutUnavailableReason from(ShortcutViolation violation) {
		return switch (violation) {
			case EMPTY, INVALID_FORMAT -> INVALID_FORMAT;
			case RESERVED -> RESERVED;
		};
	}

}
