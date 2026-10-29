package com.sniptext.global.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * 프론트가 구글 로그인으로 받은 ID 토큰을 검증한다 (명세 1번 비고).
 * 구글 공개키 서명, 발급자, 대상(우리 클라이언트 ID), 만료, 이메일 인증 여부를 모두 통과해야 계정 정보를 돌려준다.
 * 공개키는 처음 검증할 때 구글에서 받아 캐시하므로 기동할 때는 구글에 접속하지 않는다.
 * 실패 이유와 토큰 원문은 로그·예외 메시지에 남기지 않는다.
 */
@Component
public class GoogleIdTokenVerifier {

	static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
	static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
	// 구글 서버와 우리 서버의 시계 차이를 감안한다 (구글 권장). 우리 토큰은 여유 없이 Clock으로만 본다
	static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

	private static final OAuth2Error INVALID = new OAuth2Error("invalid_token");

	private final NimbusJwtDecoder decoder;

	@Autowired
	public GoogleIdTokenVerifier(@Value("${sniptext.google.client-id}") String clientId, Clock clock) {
		this(NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build(), clientId, clock);
	}

	/** 테스트에서 구글 대신 직접 만든 키로 서명한 토큰을 검증할 때 쓴다. */
	GoogleIdTokenVerifier(NimbusJwtDecoder decoder, String clientId, Clock clock) {
		if (clientId == null || clientId.isBlank()) {
			throw new IllegalStateException("sniptext.google.client-id가 비어 있습니다");
		}
		decoder.setJwtValidator(jwt -> validate(jwt, clientId, clock));
		this.decoder = decoder;
	}

	/** 검증에 실패하면 빈 값이다 (로그인 API는 이유와 상관없이 AUTH401_3으로 응답한다). 예외를 밖으로 내지 않는다. */
	public Optional<GoogleAccount> verify(String idToken) {
		try {
			Jwt jwt = decoder.decode(idToken);
			return Optional.of(new GoogleAccount(jwt.getSubject(), jwt.getClaimAsString("email"),
				jwt.getClaimAsString("name"), jwt.getClaimAsString("picture")));
		} catch (RuntimeException e) {
			// 예외 메시지에 토큰 일부가 들어갈 수 있어 로그를 남기지 않는다
			return Optional.empty();
		}
	}

	private static OAuth2TokenValidatorResult validate(Jwt jwt, String clientId, Clock clock) {
		Instant now = clock.instant();
		boolean notExpired = jwt.getExpiresAt() != null && now.isBefore(jwt.getExpiresAt().plus(CLOCK_SKEW));
		// getIssuer()는 URL로 바꿔 읽어 https가 없는 "accounts.google.com"에서 실패하므로 문자열로 본다
		boolean issuedByGoogle = ISSUERS.contains(jwt.getClaimAsString("iss"));
		List<String> audience = jwt.getAudience();
		boolean forUs = audience != null && audience.contains(clientId);
		boolean hasAccount = isPresent(jwt.getSubject()) && isPresent(jwt.getClaimAsString("email"));
		if (notExpired && issuedByGoogle && forUs && hasAccount && isEmailVerified(jwt)) {
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(INVALID);
	}

	// 구글은 불리언으로 주지만 문자열 "true"로 오는 경우도 받아 준다
	private static boolean isEmailVerified(Jwt jwt) {
		Object verified = jwt.getClaim("email_verified");
		return Boolean.TRUE.equals(verified) || "true".equals(verified);
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}

}
