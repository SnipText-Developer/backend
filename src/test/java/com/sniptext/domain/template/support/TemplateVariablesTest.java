package com.sniptext.domain.template.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateVariablesTest {

	/** 역추적이 폭발하면 수 초 이상 걸린다. CI의 느린 첫 실행도 견디도록 넉넉히 1초로 둔다. */
	private static final Duration QUICK = Duration.ofSeconds(1);

	@Test
	@DisplayName("변수를 첫 등장 순으로, 같은 이름은 한 번만 돌려준다")
	void firstAppearanceOrderWithoutDuplicates() {
		assertThat(TemplateVariables.extract("{{고객명}} {{금액}} {{고객명}}"))
			.containsExactly("고객명", "금액");
	}

	@Test
	@DisplayName("한글 완성형·자음·모음, 영문, 숫자, 밑줄을 변수 이름으로 허용한다")
	void allowedCharacters() {
		assertThat(TemplateVariables.extract("{{고객명}}{{ㄱㄴ}}{{ㅏㅣ}}{{Name}}{{2026}}{{고객_이름1}}"))
			.containsExactly("고객명", "ㄱㄴ", "ㅏㅣ", "Name", "2026", "고객_이름1");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{{}}",          // 이름 없음
		"{{ 고객명 }}",    // 공백
		"{{고객-명}}",     // 허용하지 않는 문자
		"{고객명}",       // 한 겹 중괄호
		"{{고객\n명}}",    // 줄바꿈을 걸침
		"{{고객명}",      // 닫는 중괄호 부족
	})
	@DisplayName("형식이 틀린 중괄호는 변수가 아니고 에러도 아니다")
	void notVariables(String content) {
		assertThat(TemplateVariables.extract(content)).isEmpty();
	}

	@Test
	@DisplayName("세 겹 중괄호 {{{고객명}}}은 안쪽 {{고객명}}을 변수로 본다 (명세에 없는 경우를 고정)")
	void tripleBraces() {
		assertThat(TemplateVariables.extract("{{{고객명}}}")).containsExactly("고객명");
	}

	@Test
	@DisplayName("형식이 틀린 중괄호가 섞여도 바른 변수는 뽑는다")
	void mixedWithInvalid() {
		assertThat(TemplateVariables.extract("{고객} {{ 금액 }} {{고객명}}님 {{}} {{날짜}}"))
			.containsExactly("고객명", "날짜");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@DisplayName("null·빈 문자열이면 빈 목록")
	void nullOrEmpty(String content) {
		assertThat(TemplateVariables.extract(content)).isEmpty();
	}

	@Test
	@DisplayName("돌려준 목록은 수정할 수 없다")
	void unmodifiable() {
		List<String> variables = TemplateVariables.extract("{{고객명}}");

		assertThatThrownBy(() -> variables.add("금액"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("변수 없이 \"{{\"만 1500번 반복한 3000자 본문에서도 바로 끝난다")
	void longOpeningBracesFinishQuickly() {
		String content = "{{".repeat(1500);
		assertThat(content).hasSize(3000);

		List<String> variables = assertTimeoutPreemptively(QUICK, () -> TemplateVariables.extract(content));

		assertThat(variables).isEmpty();
	}

	@Test
	@DisplayName("닫히지 않은 긴 변수 이름이 반복되는 3000자 본문에서도 바로 끝난다")
	void longUnclosedNamesFinishQuickly() {
		String content = ("{{" + "가".repeat(98)).repeat(30);
		assertThat(content).hasSize(3000);

		List<String> variables = assertTimeoutPreemptively(QUICK, () -> TemplateVariables.extract(content));

		assertThat(variables).isEmpty();
	}

}
