package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sniptext.domain.template.entity.ShortcutViolation;

class ShortcutValidatorTest {

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
		assertThat(ShortcutValidator.findViolation(shortcut)).contains(ShortcutViolation.EMPTY);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/a",            // 1자
		"/견적요청서작성안내문서", // 11자
		"견적",           // "/" 없음
		"//견적",         // 특수문자
		"/견 적",         // 공백
		" /견적",         // 앞 공백
		"/견적 ",         // 뒤 공백
		"/견적!",         // 특수문자
		"/견적_안내",      // 밑줄
		"/견적😀",        // 이모지
		"/ｑｕｏｔｅ"     // 전각 영문
	})
	@DisplayName("형식이 맞지 않으면 INVALID_FORMAT")
	void invalidFormat(String shortcut) {
		assertThat(ShortcutValidator.findViolation(shortcut)).contains(ShortcutViolation.INVALID_FORMAT);
	}

	@Test
	@DisplayName("예약어 /톤교정은 RESERVED")
	void reserved() {
		assertThat(ShortcutValidator.findViolation("/톤교정")).contains(ShortcutViolation.RESERVED);
	}

	@Test
	@DisplayName("자모 분리형으로 입력한 /톤교정도 RESERVED")
	void reservedInNfd() {
		assertThat(ShortcutValidator.findViolation(nfd("/톤교정"))).contains(ShortcutViolation.RESERVED);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"/견적",           // 2자
		"/견적요청서작성안내문", // 10자
		"/Quote",
		"/견적2",
		"/ㅎㅇ",           // 자음
		"/ㅏㅣ",           // 모음
		"/2026",
		"/톤교정2"          // 예약어와 다른 단축어
	})
	@DisplayName("형식에 맞고 예약어가 아니면 위반 없음")
	void valid(String shortcut) {
		assertThat(ShortcutValidator.findViolation(shortcut)).isEmpty();
	}

	@Test
	@DisplayName("자모 분리형으로 입력해도 정규화한 뒤 검사해 통과한다")
	void validInNfd() {
		assertThat(ShortcutValidator.findViolation(nfd("/견적"))).isEmpty();
	}

	@Test
	@DisplayName("자모 분리형 10자도 정규화하면 10자라 통과한다")
	void maxLengthInNfd() {
		assertThat(ShortcutValidator.findViolation(nfd("/견적요청서작성안내문"))).isEmpty();
	}

	@Test
	@DisplayName("정규화는 완성형으로 합치고 대소문자는 그대로 둔다")
	void normalize() {
		assertThat(ShortcutValidator.normalize(nfd("/견적Quote"))).isEqualTo("/견적Quote");
	}

	@Test
	@DisplayName("비교 키는 정규화한 뒤 소문자로 바꾼다")
	void toKey() {
		assertThat(ShortcutValidator.toKey("/Quote")).isEqualTo("/quote");
	}

	@Test
	@DisplayName("자모 분리형과 완성형으로 입력한 같은 단축어는 비교 키가 같다")
	void sameKeyForNfdAndNfc() {
		assertThat(ShortcutValidator.toKey(nfd("/견적"))).isEqualTo(ShortcutValidator.toKey("/견적"));
	}

}
