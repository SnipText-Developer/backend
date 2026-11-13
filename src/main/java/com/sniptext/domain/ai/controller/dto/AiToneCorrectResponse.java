package com.sniptext.domain.ai.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.sniptext.domain.ai.entity.ToneStyle;
import com.sniptext.domain.ai.service.dto.result.AiToneCorrectResult;
import com.sniptext.domain.ai.service.dto.result.AiToneUsageResult;
import com.sniptext.domain.ai.support.KeyInfoDiff;

/**
 * AI 톤 교정 결과 (명세 22번). 핵심 정보 차이가 없으면 keyInfoWarning은 null이다.
 * 교정 결과는 저장·로그 금지 대상이라 toString에는 길이만 보인다.
 */
public record AiToneCorrectResponse(
	String correctedText,
	ToneStyle style,
	KeyInfoWarning keyInfoWarning,
	Usage usage
) {

	/** 기획 확정 통합 문구. 화면이 그대로 띄운다 */
	static final String KEY_INFO_WARNING_MESSAGE = "원문의 일부 정보가 빠지거나 변경되었을 수 있어요. 확인 후 사용해주세요.";

	public static AiToneCorrectResponse from(AiToneCorrectResult result) {
		return new AiToneCorrectResponse(result.correctedText(), result.style(),
			KeyInfoWarning.from(result.keyInfoDiff()), Usage.from(result.usage()));
	}

	@Override
	public String toString() {
		return "AiToneCorrectResponse[correctedTextLength=%d, style=%s, keyInfoWarning=%s, usage=%s]".formatted(
			correctedText.length(), style, keyInfoWarning, usage);
	}

	public record KeyInfoWarning(String message, List<String> missingValues, List<String> addedValues) {

		static KeyInfoWarning from(KeyInfoDiff diff) {
			if (!diff.hasDifference()) {
				return null;
			}
			return new KeyInfoWarning(KEY_INFO_WARNING_MESSAGE, diff.missingValues(), diff.addedValues());
		}

		/** 값은 원문·결과 조각이라 개수만 보인다. */
		@Override
		public String toString() {
			return "KeyInfoWarning[missing=%d, added=%d]".formatted(missingValues.size(), addedValues.size());
		}

	}

	/** 시각은 전역 직렬화 설정이 +09:00을 붙여 내보낸다. */
	public record Usage(int usedCount, int limit, int remaining, LocalDateTime resetAt) {

		static Usage from(AiToneUsageResult usage) {
			return new Usage(usage.usedCount(), usage.limit(), usage.remaining(), usage.resetAt());
		}

	}

}
