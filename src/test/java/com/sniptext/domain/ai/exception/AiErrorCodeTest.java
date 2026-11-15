package com.sniptext.domain.ai.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AiErrorCodeTest {

	private static final String TEMPORARY_FAILURE = "일시적인 오류로 요청을 처리하지 못했어요. 다시 한 번 물어봐 주시겠어요?";

	static Stream<Arguments> specCodes() {
		return Stream.of(
			Arguments.of(AiErrorCode.STYLE_INVALID, 400, "AI400_1", "문체를 선택해주세요."),
			Arguments.of(AiErrorCode.TEXT_EMPTY, 400, "AI400_2", "교정할 문장을 입력해주세요."),
			Arguments.of(AiErrorCode.TEXT_TOO_LONG, 400, "AI400_3", "문장은 3,000자까지 입력할 수 있어요."),
			Arguments.of(AiErrorCode.INAPPROPRIATE_TEXT, 422, "AI422_1", "부적절한 표현이 있어 교정할 수 없어요. 수정 후 다시 시도해 주세요."),
			Arguments.of(AiErrorCode.DAILY_LIMIT_EXCEEDED, 429, "AI429_1", "오늘 톤 교정 횟수를 모두 사용했어요."),
			Arguments.of(AiErrorCode.LANGUAGE_MODEL_FAILED, 502, "AI502_1", TEMPORARY_FAILURE),
			Arguments.of(AiErrorCode.LANGUAGE_MODEL_TIMEOUT, 504, "AI504_1", TEMPORARY_FAILURE));
	}

	@ParameterizedTest
	@MethodSource("specCodes")
	@DisplayName("명세 22번의 HTTP 상태·결과 코드·문구와 같다")
	void matchesSpec(AiErrorCode errorCode, int status, String code, String message) {
		assertThat(errorCode.getHttpStatus().value()).isEqualTo(status);
		assertThat(errorCode.getCode()).isEqualTo(code);
		assertThat(errorCode.getMessage()).isEqualTo(message);
	}

	@Test
	@DisplayName("명세에 없는 에러 코드를 두지 않는다")
	void noExtraCodes() {
		assertThat(AiErrorCode.values()).hasSize((int)specCodes().count());
	}

}
