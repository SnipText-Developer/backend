package com.sniptext.domain.template.entity;

/** 단축어가 규칙을 어긴 이유. 검사 순서대로 나열한다 (저장하지 않는 값). */
public enum ShortcutViolation {

	/** 비었거나 "/"만 있음 */
	EMPTY,
	/** "/" 뒤 한글(완성형·자모)·영문·숫자 2~10자가 아님 */
	INVALID_FORMAT,
	/** 예약어 (/톤교정) */
	RESERVED

}
