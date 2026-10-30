package com.sniptext.domain.template.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import com.sniptext.domain.template.entity.ShortcutViolation;
import com.sniptext.domain.template.entity.TemplateContentViolation;
import com.sniptext.domain.template.entity.TemplateTitleViolation;

class TemplateErrorCodeTest {

	@Test
	@DisplayName("자동 번호 소진은 명세의 결과 코드와 문구다")
	void shortcutNumberExhausted() {
		assertThat(TemplateErrorCode.SHORTCUT_NUMBER_EXHAUSTED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(TemplateErrorCode.SHORTCUT_NUMBER_EXHAUSTED.getCode()).isEqualTo("TEMPLATE409_2");
		assertThat(TemplateErrorCode.SHORTCUT_NUMBER_EXHAUSTED.getMessage()).isEqualTo("사용할 수 있는 단축어 번호가 없어요.");
	}

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

	@ParameterizedTest
	@CsvSource({
		"EMPTY,    TEMPLATE400_1, 템플릿 이름을 입력해주세요.",
		"TOO_LONG, TEMPLATE400_2, 템플릿 이름은 50자까지 입력할 수 있어요."
	})
	@DisplayName("이름 위반은 명세의 결과 코드와 문구로 바뀐다")
	void fromTitleViolation(TemplateTitleViolation violation, String code, String message) {
		TemplateErrorCode errorCode = TemplateErrorCode.from(violation);

		assertThat(errorCode.getCode()).isEqualTo(code);
		assertThat(errorCode.getMessage()).isEqualTo(message);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"EMPTY    | TEMPLATE400_6 | 템플릿 내용을 입력해주세요.",
		"TOO_LONG | TEMPLATE400_7 | 템플릿 내용은 3,000자까지 입력할 수 있어요."
	})
	@DisplayName("본문 위반은 명세의 결과 코드와 문구로 바뀐다")
	void fromContentViolation(TemplateContentViolation violation, String code, String message) {
		TemplateErrorCode errorCode = TemplateErrorCode.from(violation);

		assertThat(errorCode.getCode()).isEqualTo(code);
		assertThat(errorCode.getMessage()).isEqualTo(message);
	}

}
