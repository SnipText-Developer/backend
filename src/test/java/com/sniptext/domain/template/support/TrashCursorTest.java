package com.sniptext.domain.template.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrashCursorTest {

	private static final LocalDateTime MICROS = LocalDateTime.of(2026, 10, 7, 10, 58, 0, 123_456_000);

	@Test
	@DisplayName("인코딩한 커서를 해석하면 마이크로초까지 같은 값이다")
	void roundTrip() {
		String encoded = new TrashCursor(MICROS, 18L).encode();

		assertThat(TrashCursor.decode(encoded)).contains(new TrashCursor(MICROS, 18L));
	}

	@Test
	@DisplayName("초와 소수가 0인 시각도 그대로 돌아온다")
	void roundTripWithoutFraction() {
		LocalDateTime wholeMinute = LocalDateTime.of(2026, 10, 7, 10, 58);

		String encoded = new TrashCursor(wholeMinute, 18L).encode();

		assertThat(TrashCursor.decode(encoded)).contains(new TrashCursor(wholeMinute, 18L));
	}

	@Test
	@DisplayName("쿼리 문자열에 그대로 넣을 수 있게 URL 안전 문자만 쓰고 채움 문자가 없다")
	void urlSafe() {
		assertThat(new TrashCursor(MICROS, Long.MAX_VALUE).encode()).matches("[A-Za-z0-9_-]+");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "!!!", "한글"})
	@DisplayName("Base64가 아닌 값은 해석하지 못한다")
	void notBase64(String cursor) {
		assertThat(TrashCursor.decode(cursor)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"abc",
		"2026-10-07T10:58:00.123456",
		"2026-10-07T10:58:00.123456_",
		"_18",
		"18_2026-10-07T10:58:00.123456",
		"2026-10-07T10:58:00.123456_abc",
		"2026-10-07T10:58:00.123456_-1",
		"2026-10-07T10:58:00.123456_99999999999999999999",
		"2026-02-30T10:58:00.123456_18",
		"2026-10-07T24:58:00.123456_18",
		"2026-10-07T10:58:00_18",
		"2026-10-07T10:58:00.1234567_18",
		"+12026-10-07T10:58:00.123456_18",
		"-2026-10-07T10:58:00.123456_18"
	})
	@DisplayName("서버가 만든 형식이 아니면 해석하지 못한다")
	void malformed(String decoded) {
		String cursor = Base64.getUrlEncoder().encodeToString(decoded.getBytes(StandardCharsets.UTF_8));

		assertThat(TrashCursor.decode(cursor)).isEmpty();
	}

}
