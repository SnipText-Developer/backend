package com.sniptext.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultCodeRegistryTest {

	private final ResultCodeRegistry registry = new ResultCodeRegistry();

	@Test
	@DisplayName("ResultCode를 구현한 열거형의 코드 문자열로 값을 찾는다")
	void findByCode() {
		assertThat(registry.find("COMMON400_1")).contains(CommonErrorCode.BAD_REQUEST);
		assertThat(registry.find("COMMON500_1")).contains(CommonErrorCode.INTERNAL_SERVER_ERROR);
	}

	@Test
	@DisplayName("등록되지 않은 문자열이나 null이면 빈 값을 돌려준다")
	void unknownCode() {
		assertThat(registry.find("must not be blank")).isEmpty();
		assertThat(registry.find(null)).isEmpty();
	}

}
