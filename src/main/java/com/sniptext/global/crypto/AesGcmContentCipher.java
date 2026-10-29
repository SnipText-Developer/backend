package com.sniptext.global.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM. 저장값은 {@code v1:} + Base64(IV 12바이트 ‖ 암호문 ‖ 태그 16바이트)다.
 * 버전 표시는 키를 바꿀 때 옛 키로 쓴 값을 구분하기 위해 둔다.
 * 원문·저장값·키는 예외 메시지에 넣지 않는다 (전역 예외 처리가 예외와 원인을 로그에 남긴다).
 */
public class AesGcmContentCipher implements ContentCipher {

	private static final String VERSION_PREFIX = "v1:";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int KEY_LENGTH = 32;
	private static final int IV_LENGTH = 12;
	private static final int TAG_BITS = 128;
	private static final int TAG_LENGTH = TAG_BITS / Byte.SIZE;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final SecretKey key;

	public AesGcmContentCipher(byte[] key) {
		if (key == null || key.length != KEY_LENGTH) {
			throw new IllegalStateException("본문 암호화 키는 32바이트여야 합니다");
		}
		this.key = new SecretKeySpec(key, "AES");
	}

	/** 환경 변수로 받은 Base64 키로 만든다. Base64가 아니거나 32바이트가 아니면 기동에 실패한다. */
	public static AesGcmContentCipher fromBase64Key(String base64Key) {
		if (base64Key == null) {
			throw invalidBase64Key();
		}
		byte[] key;
		try {
			key = Base64.getDecoder().decode(base64Key);
		} catch (IllegalArgumentException e) {
			// 디코더 예외 메시지에 키 글자가 들어가므로 원인을 붙이지 않는다
			throw invalidBase64Key();
		}
		if (key.length != KEY_LENGTH) {
			throw invalidBase64Key();
		}
		return new AesGcmContentCipher(key);
	}

	private static IllegalStateException invalidBase64Key() {
		return new IllegalStateException("sniptext.crypto.content-key는 Base64로 인코딩한 32바이트여야 합니다");
	}

	@Override
	public String encrypt(String plaintext) {
		Objects.requireNonNull(plaintext, "암호화할 값이 없습니다");
		byte[] iv = new byte[IV_LENGTH];
		RANDOM.nextBytes(iv);
		try {
			// Cipher는 스레드 안전하지 않아 호출마다 새로 만든다
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] body = ByteBuffer.allocate(IV_LENGTH + encrypted.length).put(iv).put(encrypted).array();
			return VERSION_PREFIX + Base64.getEncoder().encodeToString(body);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("본문을 암호화할 수 없습니다", e);
		}
	}

	@Override
	public String decrypt(String stored) {
		byte[] body = decodeBody(stored);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, body, 0, IV_LENGTH));
			byte[] decrypted = cipher.doFinal(body, IV_LENGTH, body.length - IV_LENGTH);
			return new String(decrypted, StandardCharsets.UTF_8);
		} catch (GeneralSecurityException e) {
			// 다른 키·변조면 태그 검사에서 걸린다. JDK 예외 메시지에는 값이 들어가지 않는다
			throw new ContentDecryptionException("본문을 복호화할 수 없습니다 (키가 다르거나 값이 변조됨)", e);
		}
	}

	private static byte[] decodeBody(String stored) {
		if (stored == null || !stored.startsWith(VERSION_PREFIX)) {
			throw new ContentDecryptionException("본문 저장값의 형식 버전을 알 수 없습니다");
		}
		byte[] body;
		try {
			body = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
		} catch (IllegalArgumentException e) {
			// 디코더 예외 메시지에 저장값 글자가 들어가므로 원인을 붙이지 않는다
			throw new ContentDecryptionException("본문 저장값이 Base64가 아닙니다");
		}
		if (body.length < IV_LENGTH + TAG_LENGTH) {
			throw new ContentDecryptionException("본문 저장값이 너무 짧습니다");
		}
		return body;
	}

}
