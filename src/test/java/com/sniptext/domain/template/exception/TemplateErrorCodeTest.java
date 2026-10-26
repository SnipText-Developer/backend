package com.sniptext.domain.template.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sniptext.domain.template.entity.ShortcutViolation;

class TemplateErrorCodeTest {

	@ParameterizedTest
	@CsvSource({
		"EMPTY,          TEMPLATE400_3, 호출 단축어를 입력해주세요.",
		"INVALID_FORMAT, TEMPLATE400_4, 한글·영문·숫자 2~10자로 입력해주세요.",
		"RESERVED,       TEMPLATE400_5, 사용할 수 없는 단축어예요."
	})
	@DisplayName("단축어 위반은 명세의 결과 코드와 문구로 바뀐다")
	void fromShortcutViolation(ShortcutViolation violation, String code, String message) {
		TemplateErrorCode errorCode = TemplateErrorCode.from(violation);

		assertThat(errorCode.getCode()).isEqualTo(code);
		assertThat(errorCode.getMessage()).isEqualTo(message);
	}

}
