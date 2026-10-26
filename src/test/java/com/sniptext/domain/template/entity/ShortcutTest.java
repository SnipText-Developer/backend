package com.sniptext.domain.template.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ShortcutTest {

	/** 맥에서 입력하면 들어오는 자모 분리형(NFD) 문자열 */
	private static String nfd(String text) {
		String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
		assertThat(decomposed).isNotEqualTo(text);
		return decomposed;
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "/")
	@DisplayName("비었거나 \"/\"만 있으면 EMPTY")
	void empty(String shortcut) {
		assertThat(Shortcut.findViolation(shortcut)).contains(ShortcutViolation.EMPTY);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/a",                   // 1자
		"/견적요청서작성안내문서",    // 11자
		"견적",                  // "/" 없음
		"//견적",                // 특수문자
		"/견 적",                // 공백
		" /견적",                // 앞 공백
		"/견적 ",                // 뒤 공백
		"/견적\n",               // 끝 줄바꿈
		"/견​적",             // 영 너비 공백
		"/견적!",                // 특수문자
		"/견적_안내",             // 밑줄
		"/견적😀",               // 이모지
		"/ｑｕｏｔｅ"              // 전각 영문
	})
	@DisplayName("형식이 맞지 않으면 INVALID_FORMAT")
	void invalidFormat(String shortcut) {
		assertThat(Shortcut.findViolation(shortcut)).contains(ShortcutViolation.INVALID_FORMAT);
	}

	@Test
	@DisplayName("예약어 /톤교정은 RESERVED")
	void reserved() {
		assertThat(Shortcut.findViolation("/톤교정")).contains(ShortcutViolation.RESERVED);
	}

	@Test
	@DisplayName("자모 분리형으로 입력한 /톤교정도 RESERVED")
	void reservedInNfd() {
		assertThat(Shortcut.findViolation(nfd("/톤교정"))).contains(ShortcutViolation.RESERVED);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/견적",             // 2자
		"/견적요청서작성안내문", // 10자
		"/Quote",
		"/견적2",
		"/ㅎㅇ",             // 자음
		"/ㅏㅣ",             // 모음
		"/2026",
		"/톤교정2"           // 예약어와 다른 단축어
	})
	@DisplayName("형식에 맞고 예약어가 아니면 위반 없음")
	void valid(String shortcut) {
		assertThat(Shortcut.findViolation(shortcut)).isEmpty();
	}

	@Test
	@DisplayName("자모 분리형으로 입력해도 정규화한 뒤 검사해 통과한다")
	void validInNfd() {
		assertThat(Shortcut.findViolation(nfd("/견적"))).isEmpty();
	}

	@Test
	@DisplayName("자모 분리형 10자도 정규화하면 10자라 통과한다")
	void maxLengthInNfd() {
		assertThat(Shortcut.findViolation(nfd("/견적요청서작성안내문"))).isEmpty();
	}

	@Test
	@DisplayName("표시용 값은 입력한 대소문자 그대로, 비교 키는 소문자로 만든다")
	void create() {
		Shortcut shortcut = Shortcut.create("/Quote");

		assertThat(shortcut.getValue()).isEqualTo("/Quote");
		assertThat(shortcut.getKey()).isEqualTo("/quote");
	}

	@Test
	@DisplayName("자모 분리형은 표시용 값과 비교 키 모두 완성형으로 합친다")
	void createNfd() {
		Shortcut shortcut = Shortcut.create(nfd("/견적Quote"));

		assertThat(shortcut.getValue()).isEqualTo("/견적Quote");
		assertThat(shortcut.getKey()).isEqualTo("/견적quote");
	}

	@Test
	@DisplayName("자모 분리형과 완성형으로 입력한 같은 단축어는 비교 키가 같다")
	void sameKeyForNfdAndNfc() {
		assertThat(Shortcut.create(nfd("/견적")).getKey()).isEqualTo(Shortcut.create("/견적").getKey());
	}

	static Stream<Arguments> violations() {
		return Stream.of(
			Arguments.of(null, ShortcutViolation.EMPTY),
			Arguments.of("/", ShortcutViolation.EMPTY),
			Arguments.of("/a", ShortcutViolation.INVALID_FORMAT),
			Arguments.of("/톤교정", ShortcutViolation.RESERVED)
		);
	}

	@ParameterizedTest
	@MethodSource("violations")
	@DisplayName("규칙을 어긴 단축어는 만들 수 없고, 예외 메시지에는 입력값 없이 위반 이름만 남긴다")
	void createViolation(String shortcut, ShortcutViolation expected) {
		assertThatThrownBy(() -> Shortcut.create(shortcut))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("단축어 규칙 위반: " + expected);
	}

	@Test
	@DisplayName("표시용 값이 같으면 같은 단축어이고 해시코드도 같다")
	void equalsByValue() {
		Shortcut shortcut = Shortcut.create("/견적");
		Shortcut same = Shortcut.create("/견적");

		assertThat(shortcut).isEqualTo(same);
		assertThat(shortcut).hasSameHashCodeAs(same);
	}

	@Test
	@DisplayName("자모 분리형과 완성형으로 만든 같은 단축어는 같다")
	void equalsForNfdAndNfc() {
		assertThat(Shortcut.create(nfd("/견적"))).isEqualTo(Shortcut.create("/견적"));
	}

	@Test
	@DisplayName("비교 키가 같아도 대소문자가 다르면 다른 단축어다 (/Quote → /quote는 실제 수정)")
	void notEqualsForDifferentCase() {
		assertThat(Shortcut.create("/Quote")).isNotEqualTo(Shortcut.create("/quote"));
	}

}
