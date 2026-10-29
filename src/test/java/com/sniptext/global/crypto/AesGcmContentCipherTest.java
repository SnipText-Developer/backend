package com.sniptext.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AesGcmContentCipherTest {

	private static final byte[] KEY = filled(32, (byte)1);
	private static final byte[] OTHER_KEY = filled(32, (byte)2);
	private static final String PLAINTEXT = "안녕하세요 {{고객명}}님,\n요청하신 견적을 보내드립니다.";
	/** 저장값 안의 IV·태그 길이 (바이트) */
	private static final int IV_LENGTH = 12;
	private static final int TAG_LENGTH = 16;

	private final AesGcmContentCipher cipher = new AesGcmContentCipher(KEY);

	static Stream<String> plaintexts() {
		return Stream.of(
			PLAINTEXT,
			"이모지 😀👍🏻 와 국기 🇰🇷",
			"\n앞뒤 줄바꿈과 공백  \n\n",
			"가".repeat(3000),
			"😀".repeat(1500));
	}

	@ParameterizedTest(name = "[{index}]")
	@MethodSource("plaintexts")
	@DisplayName("암호화한 값을 복호화하면 원문과 같다 (한글·이모지·줄바꿈·3000자)")
	void roundTrip(String plaintext) {
		assertThat(cipher.decrypt(cipher.encrypt(plaintext))).isEqualTo(plaintext);
	}

	@Test
	@DisplayName("같은 원문도 암호화할 때마다 저장값이 다르다 (IV를 매번 새로 만든다)")
	void differentEveryTime() {
		String first = cipher.encrypt(PLAINTEXT);
		String second = cipher.encrypt(PLAINTEXT);

		assertThat(first).isNotEqualTo(second);
		assertThat(cipher.decrypt(first)).isEqualTo(cipher.decrypt(second)).isEqualTo(PLAINTEXT);
	}

	@Test
	@DisplayName("저장값은 v1: 뒤에 Base64(IV 12바이트 ‖ 암호문 ‖ 태그 16바이트)다")
	void storedFormat() {
		String stored = cipher.encrypt(PLAINTEXT);

		assertThat(stored).startsWith("v1:").doesNotContain(PLAINTEXT);
		byte[] body = Base64.getDecoder().decode(stored.substring("v1:".length()));
		assertThat(body).hasSize(IV_LENGTH + PLAINTEXT.getBytes(StandardCharsets.UTF_8).length
			+ TAG_LENGTH);
	}

	@Test
	@DisplayName("3000자 본문(UTF-8 최대 4바이트 글자)의 저장값도 TEXT 컬럼(65,535바이트)에 들어간다")
	void fitsInTextColumn() {
		String stored = cipher.encrypt("😀".repeat(1500));

		assertThat(stored.getBytes(StandardCharsets.UTF_8).length).isLessThan(65_535);
	}

	@Test
	@DisplayName("다른 키로 복호화하면 예외다")
	void otherKeyFails() {
		String stored = cipher.encrypt(PLAINTEXT);

		assertThatThrownBy(() -> new AesGcmContentCipher(OTHER_KEY).decrypt(stored))
			.isInstanceOf(ContentDecryptionException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"iv", "ciphertext", "tag"})
	@DisplayName("IV·암호문·태그 중 한 바이트라도 바꾸면 복호화가 예외다")
	void tamperedFails(String part) {
		byte[] body = Base64.getDecoder().decode(cipher.encrypt(PLAINTEXT).substring("v1:".length()));
		int index = switch (part) {
			case "iv" -> 0;
			case "ciphertext" -> IV_LENGTH;
			default -> body.length - 1;
		};
		body[index] ^= 0x01;
		String tampered = "v1:" + Base64.getEncoder().encodeToString(body);

		assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(ContentDecryptionException.class);
	}

	static Stream<Arguments> malformedValues() {
		String validBody = cipher().encrypt(PLAINTEXT).substring("v1:".length());
		return Stream.of(
			Arguments.of("접두어 없는 평문", PLAINTEXT),
			Arguments.of("빈 문자열", ""),
			Arguments.of("접두어만", "v1:"),
			Arguments.of("Base64가 아님", "v1:@@@@"),
			Arguments.of("IV·태그보다 짧음", "v1:" + Base64.getEncoder().encodeToString(new byte[IV_LENGTH + TAG_LENGTH - 1])),
			Arguments.of("모르는 버전", "v2:" + validBody),
			Arguments.of("버전 표시 없음", validBody));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("malformedValues")
	@DisplayName("형식이 틀린 저장값은 복호화가 예외다")
	void malformedFails(String description, String stored) {
		assertThatThrownBy(() -> cipher.decrypt(stored)).isInstanceOf(ContentDecryptionException.class);
	}

	@Test
	@DisplayName("null은 암호화·복호화하지 않는다")
	void nullRejected() {
		assertThatThrownBy(() -> cipher.encrypt(null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> cipher.decrypt(null)).isInstanceOf(ContentDecryptionException.class);
	}

	@Test
	@DisplayName("복호화 예외의 메시지와 원인에 원문·저장값·키가 없다")
	void exceptionHasNoSecrets() {
		String stored = cipher.encrypt(PLAINTEXT);
		String keyBase64 = Base64.getEncoder().encodeToString(OTHER_KEY);

		assertNoSecrets(catchFailure(() -> new AesGcmContentCipher(OTHER_KEY).decrypt(stored)),
			PLAINTEXT, stored, keyBase64);
		assertNoSecrets(catchFailure(() -> cipher.decrypt("v1:" + "@" + stored.substring(4))), stored.substring(4, 10));
		assertNoSecrets(catchFailure(() -> cipher.decrypt(PLAINTEXT)), PLAINTEXT, "안녕하세요");
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 16, 24, 31, 33, 64})
	@DisplayName("키가 32바이트가 아니면 만들 수 없다")
	void keyLength(int length) {
		assertThatThrownBy(() -> new AesGcmContentCipher(new byte[length]))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("Base64로 인코딩한 32바이트 키로 만들 수 있다")
	void fromBase64Key() {
		String keyBase64 = Base64.getEncoder().encodeToString(KEY);

		AesGcmContentCipher fromKey = AesGcmContentCipher.fromBase64Key(keyBase64);

		assertThat(fromKey.decrypt(cipher.encrypt(PLAINTEXT))).isEqualTo(PLAINTEXT);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "not-base64-key!!", "AQEBAQEBAQEBAQEBAQEBAQ==", // 16바이트
		"AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ=="}) // 31바이트
	@DisplayName("키가 Base64가 아니거나 32바이트가 아니면 예외이고 메시지에 키가 없다")
	void invalidBase64Key(String keyBase64) {
		assertThatThrownBy(() -> AesGcmContentCipher.fromBase64Key(keyBase64))
			.isInstanceOf(IllegalStateException.class)
			.satisfies(e -> {
				if (keyBase64 != null && !keyBase64.isBlank()) {
					assertNoSecrets(e, keyBase64);
				}
			});
	}

	private static AesGcmContentCipher cipher() {
		return new AesGcmContentCipher(KEY);
	}

	private static byte[] filled(int length, byte value) {
		byte[] bytes = new byte[length];
		Arrays.fill(bytes, value);
		return bytes;
	}

	private static Throwable catchFailure(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException e) {
			return e;
		}
		throw new AssertionError("예외가 나야 한다");
	}

	/** 예외와 원인 사슬 전체의 메시지에 비밀 값이 없는지 본다 (전역 예외 처리가 원인까지 로그에 남긴다) */
	private static void assertNoSecrets(Throwable thrown, String... secrets) {
		for (Throwable t = thrown; t != null; t = t.getCause()) {
			String message = String.valueOf(t.getMessage());
			for (String secret : secrets) {
				assertThat(message).doesNotContain(secret);
			}
		}
	}

}
