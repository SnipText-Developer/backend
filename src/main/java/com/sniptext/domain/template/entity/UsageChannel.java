package com.sniptext.domain.template.entity;

import java.util.Arrays;

/**
 * 템플릿 사용 채널 (명세 공통 규칙 6장). 상수 이름이 API 값이다.
 * 저장하지 않는 값이다. 채널별 통계는 화면이 분석 도구 이벤트로 따로 보낸다.
 */
public enum UsageChannel {

	/** 대시보드에서 복사 */
	DASHBOARD_COPY,
	/** 확장 팝업에서 복사 */
	POPUP_COPY,
	/** 메일 작성 창에 인라인 삽입 */
	EMAIL_INLINE;

	/** 상수 이름과 정확히 같을 때만 지원한다. 대소문자·앞뒤 공백을 고쳐 주지 않는다 */
	public static boolean isSupported(String value) {
		return Arrays.stream(values()).anyMatch(channel -> channel.name().equals(value));
	}

}
