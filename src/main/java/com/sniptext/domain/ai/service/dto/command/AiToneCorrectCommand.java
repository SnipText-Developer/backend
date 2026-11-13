package com.sniptext.domain.ai.service.dto.command;

import com.sniptext.domain.ai.entity.ToneStyle;

/**
 * AI 톤 교정 입력. 원문은 요청 단계에서 형식 검증을 마친 그대로다.
 * 원문은 저장·로그 금지 대상이라 toString에는 길이만 보인다.
 */
public record AiToneCorrectCommand(Long userId, ToneStyle style, String text) {

	@Override
	public String toString() {
		return "AiToneCorrectCommand[userId=%d, style=%s, textLength=%d]".formatted(userId, style, text.length());
	}

}
