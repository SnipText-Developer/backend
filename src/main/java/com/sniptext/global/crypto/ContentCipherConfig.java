package com.sniptext.global.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 본문 암호화 키는 환경 변수(CONTENT_ENCRYPTION_KEY)로만 받는다. 기본값이 없어 빠뜨리면 기동에 실패한다. */
@Configuration
public class ContentCipherConfig {

	@Bean
	public ContentCipher contentCipher(@Value("${sniptext.crypto.content-key}") String base64Key) {
		return AesGcmContentCipher.fromBase64Key(base64Key);
	}

}
