package com.sniptext.domain.ai.service.dto.result;

import com.sniptext.domain.ai.entity.ToneStyle;
import com.sniptext.domain.ai.support.KeyInfoDiff;

/**
 * AI 톤 교정 결과. 교정 결과는 저장·로그 금지 대상이라 toString에는 길이만 보인다.
 *
 * @param correctedText 앞뒤 공백을 지운 교정 결과
 * @param keyInfoDiff   원문과 결과의 핵심 정보 차이 (차이가 없으면 두 목록이 빈 값)
 * @param usage         선점 후 사용량
 */
public record AiToneCorrectResult(String correctedText, ToneStyle style, KeyInfoDiff keyInfoDiff,
	AiToneUsageResult usage) {

	@Override
	public String toString() {
		return "AiToneCorrectResult[correctedTextLength=%d, style=%s, keyInfoDiff=%s, usage=%s]".formatted(
			correctedText.length(), style, keyInfoDiff, usage);
	}

}
