package com.sniptext.domain.ai.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiErrorCodeTest {

	@Test
	@DisplayName("하루 한도 소진은 명세의 429 결과 코드와 문구다")
	void dailyLimitExceeded() {
		AiErrorCode errorCode = AiErrorCode.DAILY_LIMIT_EXCEEDED;

		assertThat(errorCode.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(errorCode.getCode()).isEqualTo("AI429_1");
		assertThat(errorCode.getMessage()).isEqualTo("오늘 톤 교정 횟수를 모두 사용했어요.");
	}

}
