package com.sniptext.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NicknameNormalizerTest {

	private static final String EMAIL = "snip.user@gmail.com";

	@Test
	@DisplayName("구글 이름의 앞뒤 공백을 지운다 (가운데 공백은 그대로)")
	void strip() {
		assertThat(NicknameNormalizer.normalize("  홍 길동\t", EMAIL)).isEqualTo("홍 길동");
	}

	@Test
	@DisplayName("화면과 같은 기준으로 NBSP·BOM도 앞뒤 공백으로 본다")
	void stripLikeScreen() {
		assertThat(NicknameNormalizer.normalize(" ﻿홍길동 ", EMAIL)).isEqualTo("홍길동");
	}

	@Test
	@DisplayName("50자는 그대로, 51자는 50자로 자른다")
	void truncateAt50() {
		String fifty = "가".repeat(50);

		assertThat(NicknameNormalizer.normalize(fifty, EMAIL)).isEqualTo(fifty);
		assertThat(NicknameNormalizer.normalize(fifty + "나", EMAIL)).isEqualTo(fifty);
	}

	@Test
	@DisplayName("앞뒤 공백을 지운 뒤 길이를 센다")
	void stripBeforeCount() {
		String fifty = "가".repeat(50);

		assertThat(NicknameNormalizer.normalize("   " + fifty + "   ", EMAIL)).isEqualTo(fifty);
	}

	@Test
	@DisplayName("자모 분리형(맥 입력) 이름은 NFC로 합친 뒤 길이를 센다: 50음절은 자르지 않는다")
	void normalizeNfcBeforeCount() {
		String decomposedFifty = Normalizer.normalize("가".repeat(50), Normalizer.Form.NFD);

		assertThat(decomposedFifty).hasSize(100);
		assertThat(NicknameNormalizer.normalize(decomposedFifty, EMAIL)).isEqualTo("가".repeat(50));
	}

	@Test
	@DisplayName("50번째 칸에서 이모지가 시작되면 깨진 반쪽을 남기지 않고 49칸으로 자른다")
	void doNotSplitEmoji() {
		String name = "가".repeat(49) + "😀" + "나";

		String nickname = NicknameNormalizer.normalize(name, EMAIL);

		assertThat(nickname).isEqualTo("가".repeat(49));
		assertThat(Character.isHighSurrogate(nickname.charAt(nickname.length() - 1))).isFalse();
	}

	@Test
	@DisplayName("자른 끝이 공백이면 그 공백도 지운다")
	void stripAfterTruncate() {
		String name = "가".repeat(49) + " 나";

		assertThat(NicknameNormalizer.normalize(name, EMAIL)).isEqualTo("가".repeat(49));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", " "})
	@DisplayName("구글 이름이 없거나 공백뿐이면 이메일 앞부분을 쓴다")
	void fallbackToEmail(String name) {
		assertThat(NicknameNormalizer.normalize(name, EMAIL)).isEqualTo("snip.user");
	}

	@Test
	@DisplayName("이메일 앞부분도 50자가 넘으면 자른다")
	void truncateEmailLocalPart() {
		String email = "a".repeat(60) + "@gmail.com";

		assertThat(NicknameNormalizer.normalize(null, email)).isEqualTo("a".repeat(50));
	}

}
