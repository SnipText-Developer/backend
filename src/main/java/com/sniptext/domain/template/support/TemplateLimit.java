package com.sniptext.domain.template.support;

import com.sniptext.global.policy.UsageLimits;

/**
 * 보유 템플릿 한도 규칙. 활성 템플릿(휴지통 제외)이 한도 이상이면 더 만들 수 없다.
 * 저장 차단(TemplateCommandService)과 사용량 화면 표시(사용량 API)가 같은 기준을 쓰도록 여기 한 곳에 둔다.
 * 한도 숫자는 UsageLimits에서 가져온다.
 */
public final class TemplateLimit {

	private TemplateLimit() {
	}

	public static boolean isReached(long activeCount) {
		return activeCount >= UsageLimits.TEMPLATE_LIMIT;
	}

	/** 더 만들 수 있는 개수. 한도 이상이면 0이다 */
	public static int remaining(long activeCount) {
		return Math.toIntExact(Math.max(UsageLimits.TEMPLATE_LIMIT - activeCount, 0));
	}

}
