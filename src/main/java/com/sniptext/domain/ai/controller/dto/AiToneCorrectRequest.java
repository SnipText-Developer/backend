package com.sniptext.domain.ai.controller.dto;

import com.sniptext.domain.ai.annotation.ValidToneStyle;
import com.sniptext.domain.ai.annotation.ValidToneText;
import com.sniptext.domain.ai.entity.ToneStyle;
import com.sniptext.domain.ai.service.dto.command.AiToneCorrectCommand;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 톤 교정 (명세 22번). 여러 개가 틀리면 선언 순서상 첫 에러 하나만 내려주므로 style → text 순서를 지킨다.
 * style을 ToneStyle로 받으면 없는 값이 JSON 변환 실패(COMMON400_1)가 되므로 문자열로 받는다.
 * 원문은 저장·로그 금지 대상이라 toString에는 길이만 보인다 (디버그 로그가 요청 객체를 toString으로 찍는다).
 * 스웨거의 style 허용 값은 ToneStyle 상수와 같아야 한다 (AiApiDocsTest가 확인).
 */
public record AiToneCorrectRequest(
	@Schema(description = "문체 코드 1개. BOSS_EXTERNAL(상사/외부용) / REPORT_OFFICIAL(보고/공문용) / MARKETING(마케팅/설득) / "
		+ "NOTICE(공지/안내) / SOFT_POLITE(부드러운 존댓말) / CASUAL(편한 반말)",
		allowableValues = {"BOSS_EXTERNAL", "REPORT_OFFICIAL", "MARKETING", "NOTICE", "SOFT_POLITE", "CASUAL"},
		requiredMode = Schema.RequiredMode.REQUIRED, example = "BOSS_EXTERNAL")
	@ValidToneStyle String style,
	@Schema(description = "교정할 원문. 공백만 입력은 안 되고, 3000자까지 (받은 그대로 String.length()로 셈)",
		requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 3000,
		example = "이번 달은 일정이 꽉 차서 외주 못 받는다고 해줘")
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
