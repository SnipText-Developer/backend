package com.sniptext.global.policy;

/**
 * 사용 한도. 모든 사용자에게 같다 (플랜 구분 없음). 한도 숫자는 여기 한 곳에서만 쓴다.
 * 화면은 숫자를 직접 쓰지 않고 사용량 API의 limit을 쓴다.
 */
public final class UsageLimits {

	/** 보유 템플릿 한도 (휴지통 제외 개수 기준) */
	public static final int TEMPLATE_LIMIT = 50;

	/** AI 톤 교정 하루 한도 (KST 날짜 기준) */
	public static final int AI_TONE_DAILY_LIMIT = 5;

	private UsageLimits() {
	}

}
