package com.sniptext.fixture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import com.sniptext.domain.auth.entity.ClientType;
import com.sniptext.domain.auth.entity.RefreshToken;

/** 테스트용 재발급 토큰. 해시와 계보 ID는 길이만 맞춘 가짜 값이다. 생성 인자가 바뀌면 여기만 고친다. */
public final class RefreshTokenFixture {

	public static final String FAMILY_ID = "00000000-0000-0000-0000-000000000001";
	public static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 8, 10, 0);

	private RefreshTokenFixture() {
	}

	/**
	 * 원문 토큰을 DB에 저장되는 값(SHA-256, 소문자 16진수 64자)으로 바꾼다. 응답 쿠키의 원문으로 저장된 행을 찾을 때 쓴다.
	 * 발급 도구의 해시를 빌리지 않고 따로 계산해, 저장 형식이 바뀌면 테스트가 알아채게 한다.
	 */
	public static String hashOf(String rawToken) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** n번째 가짜 해시 (16진수 64자) */
	public static String hash(int n) {
		return String.format("%064x", n);
	}

	public static RefreshToken web(Long userId, String tokenHash) {
		return web(userId, tokenHash, FAMILY_ID);
	}

	public static RefreshToken web(Long userId, String tokenHash, String familyId) {
		return RefreshToken.create(userId, ClientType.WEB, tokenHash, familyId, EXPIRES_AT);
	}

	public static RefreshToken extension(Long userId, String tokenHash, String familyId) {
		return RefreshToken.create(userId, ClientType.EXTENSION, tokenHash, familyId, EXPIRES_AT);
	}

}
