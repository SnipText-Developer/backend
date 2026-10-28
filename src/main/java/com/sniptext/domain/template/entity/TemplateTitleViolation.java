package com.sniptext.domain.template.entity;

/**
 * 템플릿 이름이 규칙을 어긴 이유. 검사 순서대로 나열한다 (저장하지 않는 값).
 * API로 그대로 내보내지 않는 내부 값이다.
 */
public enum TemplateTitleViolation {

	/** 비었거나 공백만 있음 */
	EMPTY,
	/** 앞뒤 공백 제거 후 50자 초과 */
	TOO_LONG

}
