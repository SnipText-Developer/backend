package com.sniptext.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.auth.entity.ClientType;
import com.sniptext.domain.auth.entity.RefreshToken;
import com.sniptext.domain.auth.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

/**
 * 재발급 토큰 발급. 원문은 쿠키로만 내보내고 DB에는 SHA-256 해시만 저장한다.
 * 토큰 저장이 회원 저장과 함께 커밋되도록 호출자 트랜잭션 안에서만 동작하게 강제한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenIssuer {

	public static final Duration TTL = Duration.ofDays(14);

	private static final int TOKEN_BYTES = 32;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository refreshTokenRepository;
	private final Clock clock;

	/** 새 로그인의 웹 재발급 토큰을 저장하고 원문을 돌려준다. 새 로그인이므로 계보(family_id)도 새로 만든다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public String issueForNewLogin(Long userId) {
		String rawToken = generate();
		refreshTokenRepository.save(RefreshToken.create(userId, ClientType.WEB, hash(rawToken),
			UUID.randomUUID().toString(), LocalDateTime.now(clock).plus(TTL)));
		return rawToken;
	}

	private static String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없는 환경이다", e);
		}
	}

}
