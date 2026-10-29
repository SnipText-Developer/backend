package com.sniptext.global.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 문자열 컬럼을 암호화해 저장하는 JPA 변환기. 쓰는 엔티티 필드에 {@code @Convert}로 직접 붙인다 (autoApply 아님).
 * 스프링 부트가 Hibernate에 스프링 빈 컨테이너를 연결하므로 생성자로 ContentCipher 빈을 주입받는다.
 * 기본 생성자가 없어서 주입 없이 Hibernate가 직접 만들면 기동에 실패한다 (키를 정적 필드에 두는 우회를 막는다).
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

	private final ContentCipher cipher;

	public EncryptedStringConverter(ContentCipher cipher) {
		this.cipher = cipher;
	}

	@Override
	public String convertToDatabaseColumn(String attribute) {
		return attribute == null ? null : cipher.encrypt(attribute);
	}

	@Override
	public String convertToEntityAttribute(String dbData) {
		return dbData == null ? null : cipher.decrypt(dbData);
	}

}
