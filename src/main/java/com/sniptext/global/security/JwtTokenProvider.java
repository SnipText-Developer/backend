package com.sniptext.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

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
 * 접근 토큰·가입용 토큰 발급·해석 (HS256). 두 토큰은 같은 키로 서명하고 typ으로 구분한다.
 * 접근 토큰: sub=회원 번호, typ=ACCESS, 유효 30분. 가입용 토큰: sub=구글 계정 고유값, typ=SIGNUP, 유효 30분.
 * 발급 시각과 만료 검사는 모두 Clock 빈 기준이다. 토큰 원문과 서명 키는 로그·예외 메시지에 남기지 않는다.
 */
@Component
public class JwtTokenProvider {

	static final String TYPE_CLAIM = "typ";
	static final String ACCESS_TYPE = "ACCESS";
	static final String SIGNUP_TYPE = "SIGNUP";
	static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(30);
	static final Duration SIGNUP_TOKEN_TTL = Duration.ofMinutes(30);

	private static final String EMAIL_CLAIM = "email";
	private static final String NAME_CLAIM = "name";
	private static final String PICTURE_CLAIM = "picture";

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
		return encode(claims);
	}

	/**
	 * 처음 온 사용자에게 주는 가입용 토큰. 서명만 하고 암호화하지 않으므로 담긴 값은 토큰을 받은 본인이 읽을 수 있다
	 * (이메일·이름·프로필 이미지는 로그인 응답에도 그대로 내려가는 값이다).
	 */
	public String issueSignupToken(SignupTokenClaims signup) {
		Instant now = clock.instant();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.subject(signup.providerUserId())
			.claim(TYPE_CLAIM, SIGNUP_TYPE)
			.claim(EMAIL_CLAIM, signup.email())
			.issuedAt(now)
			.expiresAt(now.plus(SIGNUP_TOKEN_TTL));
		// 값이 없는 클레임은 넣지 않는다 (해석할 때 null로 돌아온다)
		if (signup.name() != null) {
			claims.claim(NAME_CLAIM, signup.name());
		}
		if (signup.profileImageUrl() != null) {
			claims.claim(PICTURE_CLAIM, signup.profileImageUrl());
		}
		return encode(claims.build());
	}

	/** 만료·위조·종류가 다른 토큰은 모두 빈 값이다 (가입 API는 구분 없이 AUTH401_4로 응답한다). 예외를 밖으로 내지 않는다. */
	public Optional<SignupTokenClaims> parseSignupToken(String token) {
		try {
			Jwt jwt = decoder.decode(token);
			if (!SIGNUP_TYPE.equals(jwt.getClaimAsString(TYPE_CLAIM)) || jwt.getExpiresAt() == null
				|| !clock.instant().isBefore(jwt.getExpiresAt())) {
				return Optional.empty();
			}
			String providerUserId = jwt.getSubject();
			String email = jwt.getClaimAsString(EMAIL_CLAIM);
			if (providerUserId == null || providerUserId.isBlank() || email == null || email.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(new SignupTokenClaims(providerUserId, email, jwt.getClaimAsString(NAME_CLAIM),
				jwt.getClaimAsString(PICTURE_CLAIM)));
		} catch (RuntimeException e) {
			// 예외 메시지에 토큰 일부가 들어갈 수 있어 로그를 남기지 않는다
			return Optional.empty();
		}
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

	private String encode(JwtClaimsSet claims) {
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
