package com.sniptext.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * 접근 토큰 발급·해석 (HS256). sub=회원 번호, typ=ACCESS, 유효 30분.
 * 발급 시각과 만료 검사는 모두 Clock 빈 기준이다. 토큰 원문과 서명 키는 로그·예외 메시지에 남기지 않는다.
 */
@Component
public class JwtTokenProvider {

	static final String TYPE_CLAIM = "typ";
	static final String ACCESS_TYPE = "ACCESS";
	static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(30);

	private static final int MIN_SECRET_BYTES = 32;
	private static final String HMAC_SHA256 = "HmacSHA256";

	private final NimbusJwtEncoder encoder;
	private final NimbusJwtDecoder decoder;
	private final Clock clock;

	public JwtTokenProvider(@Value("${sniptext.jwt.secret}") String secret, Clock clock) {
		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("sniptext.jwt.secret은 32바이트 이상이어야 합니다");
		}
		SecretKey key = new SecretKeySpec(secretBytes, HMAC_SHA256);
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		// 기본 검증기는 시스템 시각 기준이고 60초 여유를 두므로 끄고, 만료는 Clock으로 직접 본다
		this.decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
		this.clock = clock;
	}

	public String issueAccessToken(Long userId) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(String.valueOf(userId))
			.claim(TYPE_CLAIM, ACCESS_TYPE)
			.issuedAt(now)
			.expiresAt(now.plus(ACCESS_TOKEN_TTL))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/** 어떤 문자열이 와도 예외를 밖으로 내지 않는다 (깨진 토큰이 500이 되지 않게). */
	public AccessTokenResult parseAccessToken(String token) {
		try {
			Jwt jwt = decoder.decode(token);
			if (!ACCESS_TYPE.equals(jwt.getClaimAsString(TYPE_CLAIM)) || jwt.getExpiresAt() == null) {
				return new AccessTokenResult.Invalid();
			}
			long userId = Long.parseLong(jwt.getSubject());
			if (!clock.instant().isBefore(jwt.getExpiresAt())) {
				return new AccessTokenResult.Expired();
			}
			return new AccessTokenResult.Authenticated(userId);
		} catch (RuntimeException e) {
			// 예외 메시지에 토큰 일부가 들어갈 수 있어 로그를 남기지 않는다
			return new AccessTokenResult.Invalid();
		}
	}

}
