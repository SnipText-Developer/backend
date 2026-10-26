package com.sniptext.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** 발급 도구로는 만들 수 없는 토큰(다른 종류, 변조)을 테스트에서 만든다. */
public final class TestTokens {

	private TestTokens() {
	}

	/** 주어진 키로 서명한 토큰. typ·sub를 마음대로 넣어 종류가 다른 토큰이나 깨진 회원 번호를 만든다. */
	public static String sign(String secret, String type, String subject, Instant expiresAt) {
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
			new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(subject)
			.claim("typ", type)
			.issuedAt(expiresAt.minus(Duration.ofMinutes(30)))
			.expiresAt(expiresAt)
			.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

	/** 서명 첫 글자를 바꾼다. 마지막 글자는 패딩 비트만 바뀌어 같은 서명일 수 있어 피한다. */
	public static String tamperSignature(String token) {
		int signatureStart = token.lastIndexOf('.') + 1;
		char replaced = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
		return token.substring(0, signatureStart) + replaced + token.substring(signatureStart + 1);
	}

}
