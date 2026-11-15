package com.sniptext.domain.ai.controller.dto;

import com.sniptext.domain.ai.annotation.ValidToneStyle;
import com.sniptext.domain.ai.annotation.ValidToneText;
import com.sniptext.domain.ai.entity.ToneStyle;
import com.sniptext.domain.ai.service.dto.command.AiToneCorrectCommand;

/**
 * AI 톤 교정 (명세 22번). 여러 개가 틀리면 선언 순서상 첫 에러 하나만 내려주므로 style → text 순서를 지킨다.
 * style을 ToneStyle로 받으면 없는 값이 JSON 변환 실패(COMMON400_1)가 되므로 문자열로 받는다.
 * 원문은 저장·로그 금지 대상이라 toString에는 길이만 보인다 (디버그 로그가 요청 객체를 toString으로 찍는다).
 */
public record AiToneCorrectRequest(
	@ValidToneStyle String style,
	@ValidToneText String text
) {

	public AiToneCorrectCommand toCommand(Long userId) {
		return new AiToneCorrectCommand(userId, ToneStyle.valueOf(style), text);
	}

	@Override
	public String toString() {
		return "AiToneCorrectRequest[style=%s, textLength=%s]".formatted(style, text == null ? null : text.length());
	}

}
