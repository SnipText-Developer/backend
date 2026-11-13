package com.sniptext.domain.ai.exception;

import org.springframework.http.HttpStatus;

import com.sniptext.global.response.ResultCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ResultCode {

	/** style 누락 또는 없는 값 */
	STYLE_INVALID(HttpStatus.BAD_REQUEST, "AI400_1", "문체를 선택해주세요."),
	/** text가 비었거나 공백만 입력 */
	TEXT_EMPTY(HttpStatus.BAD_REQUEST, "AI400_2", "교정할 문장을 입력해주세요."),
	/** text 3000자 초과 */
	TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "AI400_3", "문장은 3,000자까지 입력할 수 있어요."),
	/** 혐오·차별 표현 또는 욕설·비속어 감지. 횟수 미차감 */
	INAPPROPRIATE_TEXT(HttpStatus.UNPROCESSABLE_CONTENT, "AI422_1", "부적절한 표현이 있어 교정할 수 없어요. 수정 후 다시 시도해 주세요."),
	/** 하루 한도 소진. 자정(KST)에 리셋 */
	DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI429_1", "오늘 톤 교정 횟수를 모두 사용했어요."),
	/** 외부 AI 오류. 횟수 미차감 */
	LANGUAGE_MODEL_FAILED(HttpStatus.BAD_GATEWAY, "AI502_1", "일시적인 오류로 요청을 처리하지 못했어요. 다시 한 번 물어봐 주시겠어요?"),
	/** 외부 AI 20초 타임아웃. 횟수 미차감 */
	LANGUAGE_MODEL_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI504_1", "일시적인 오류로 요청을 처리하지 못했어요. 다시 한 번 물어봐 주시겠어요?");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

}
