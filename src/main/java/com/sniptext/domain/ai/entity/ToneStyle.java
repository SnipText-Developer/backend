package com.sniptext.domain.ai.entity;

/**
 * AI 톤 교정 문체 (명세 공통 규칙 6장). 상수 이름이 API 값이다.
 * 저장하지 않는 값이라 쓰는 도메인(ai)의 entity 패키지에 둔다. 문체별 지시문은 ToneCorrectionPrompt에 있다.
 */
public enum ToneStyle {

	BOSS_EXTERNAL,
	REPORT_OFFICIAL,
	MARKETING,
	NOTICE,
	SOFT_POLITE,
	CASUAL,
	ESSAY

}
