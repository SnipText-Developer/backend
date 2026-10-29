package com.sniptext.global.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EncryptedStringConverterTest {

	private final ContentCipher cipher = new AesGcmContentCipher(key());
	private final EncryptedStringConverter converter = new EncryptedStringConverter(cipher);

	@Test
	@DisplayName("DB에는 암호화한 값을 넣고, 읽을 때 원문으로 되돌린다")
	void roundTrip() {
		String column = converter.convertToDatabaseColumn("견적 안내 본문");

		assertThat(column).startsWith("v1:").doesNotContain("견적");
		assertThat(converter.convertToEntityAttribute(column)).isEqualTo("견적 안내 본문");
	}

	@Test
	@DisplayName("주입받은 암호화 도구로 암호화한다")
	void usesInjectedCipher() {
		String column = converter.convertToDatabaseColumn("견적 안내 본문");

		assertThat(cipher.decrypt(column)).isEqualTo("견적 안내 본문");
	}

	@Test
	@DisplayName("null은 양쪽 모두 null 그대로 둔다")
	void nullPassesThrough() {
		assertThat(converter.convertToDatabaseColumn(null)).isNull();
		assertThat(converter.convertToEntityAttribute(null)).isNull();
	}

	private static byte[] key() {
		byte[] key = new byte[32];
		Arrays.fill(key, (byte)3);
		return key;
	}

}
