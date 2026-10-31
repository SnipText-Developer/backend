package com.sniptext.global.client.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sniptext.support.MutableClock;
import com.sun.net.httpserver.HttpServer;

/**
 * 구글 대신 테스트에서 만든 RSA 키로 ID 토큰을 서명해 검증 규칙을 확인한다 (구글에 접속하지 않는다).
 * 공개키 조회 실패·시간 초과는 로컬 가짜 공개키 서버로 본다.
 */
class GoogleIdTokenVerifierTest {

	private static final String CLIENT_ID = "sniptext-web.apps.googleusercontent.com";

	private static KeyPair googleKey;
	private static KeyPair otherKey;

	private MutableClock clock;
	private GoogleIdTokenVerifier verifier;
	private HttpServer keyServer;
	private final CountDownLatch releaseKeyServer = new CountDownLatch(1);

	@BeforeAll
	static void createKeys() throws NoSuchAlgorithmException {
		googleKey = newRsaKey();
		otherKey = newRsaKey();
	}

	@BeforeEach
	void setUp() {
		clock = new MutableClock(LocalDateTime.of(2026, 10, 29, 21, 0));
		verifier = new GoogleIdTokenVerifier(
			NimbusJwtDecoder.withPublicKey((RSAPublicKey)googleKey.getPublic()).build(), CLIENT_ID, clock);
	}

	@AfterEach
	void tearDown() {
		releaseKeyServer.countDown();
		if (keyServer != null) {
			keyServer.stop(0);
		}
	}

	@Test
	@DisplayName("구글이 서명한 유효한 ID 토큰이면 계정 정보(sub·이메일·이름·사진)를 돌려준다")
	void valid() {
		String token = sign(googleKey, claims -> { });

		assertThat(verifier.verify(token)).contains(new GoogleAccount(
			"109876543210987654321", "user@gmail.com", "홍길동", "https://lh3.googleusercontent.com/a/sample"));
	}

	@Test
	@DisplayName("발급자는 https가 붙은 형태도 받는다")
	void issuerWithScheme() {
		String token = sign(googleKey, claims -> claims.issuer("https://accounts.google.com"));

		assertThat(verifier.verify(token)).isPresent();
	}

	@Test
	@DisplayName("이름·사진이 없어도 검증은 통과하고 그 값은 null이다")
	void withoutNameAndPicture() {
		String token = sign(googleKey, claims -> claims.claims(map -> {
			map.remove("name");
			map.remove("picture");
		}));

		GoogleAccount account = verifier.verify(token).orElseThrow();
		assertThat(account.name()).isNull();
		assertThat(account.pictureUrl()).isNull();
	}

	@Test
	@DisplayName("다른 키로 서명했으면 빈 값이다 (구글 공개키 서명 검사)")
	void otherSigningKey() {
		assertThat(verifier.verify(sign(otherKey, claims -> { }))).isEmpty();
	}

	@Test
	@DisplayName("발급자가 구글이 아니면 빈 값이다")
	void otherIssuer() {
		assertThat(verifier.verify(sign(googleKey, claims -> claims.issuer("https://evil.example.com")))).isEmpty();
	}

	@Test
	@DisplayName("대상이 우리 클라이언트 ID가 아니면 빈 값이다 (다른 앱용으로 받은 토큰)")
	void otherAudience() {
		assertThat(verifier.verify(sign(googleKey, claims -> claims.audience(List.of("other-app"))))).isEmpty();
	}

	@Test
	@DisplayName("만료 뒤 60초까지는 통과하고 그 뒤에는 빈 값이다 (구글과의 시계 차이 여유)")
	void expiry() {
		String token = sign(googleKey, claims -> claims
			.issuedAt(clock.instant().minus(Duration.ofHours(1)))
			.expiresAt(clock.instant()));

		clock.advance(Duration.ofSeconds(59));
		assertThat(verifier.verify(token)).isPresent();

		clock.advance(Duration.ofSeconds(1));
		assertThat(verifier.verify(token)).isEmpty();
	}

	@Test
	@DisplayName("이메일 인증을 하지 않은 계정이면 빈 값이다")
	void emailNotVerified() {
		assertThat(verifier.verify(sign(googleKey, claims -> claims.claim("email_verified", false)))).isEmpty();
		assertThat(verifier.verify(sign(googleKey, claims -> claims.claims(map -> map.remove("email_verified")))))
			.isEmpty();
	}

	@Test
	@DisplayName("이메일 인증 여부가 문자열 \"true\"로 와도 통과한다")
	void emailVerifiedAsString() {
		assertThat(verifier.verify(sign(googleKey, claims -> claims.claim("email_verified", "true")))).isPresent();
	}

	@Test
	@DisplayName("이메일이 없으면 빈 값이다")
	void withoutEmail() {
		assertThat(verifier.verify(sign(googleKey, claims -> claims.claims(map -> map.remove("email"))))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "a.b.c", "", "   ", "한글!@#.토큰$%^.깨짐&*()"})
	@DisplayName("형식이 깨진 문자열은 예외 없이 빈 값이다")
	void malformed(String token) {
		assertThat(verifier.verify(token)).isEmpty();
	}

	@Test
	@DisplayName("구글 계정 고유값(sub)이 없으면 빈 값이다")
	void withoutSubject() {
		assertThat(verifier.verify(sign(googleKey, claims -> claims.claims(map -> map.remove("sub"))))).isEmpty();
	}

	@Test
	@DisplayName("공개키 주소에서 키를 받아 검증한다 (운영과 같은 시간 제한 RestTemplate 사용)")
	void fetchesKeysFromJwkSetUri() throws IOException {
		String jwks = new JWKSet(new RSAKey.Builder((RSAPublicKey)googleKey.getPublic()).keyID("test-key").build())
			.toString();
		GoogleIdTokenVerifier real = realVerifier(startKeyServer(200, jwks, false), Duration.ofSeconds(2));

		assertThat(real.verify(sign(googleKey, claims -> { }))).isPresent();
		assertThat(real.verify(sign(otherKey, claims -> { }))).isEmpty();
	}

	@Test
	@DisplayName("공개키 주소가 오류를 주면 토큰 탓이 아니므로 GoogleLoginException이고 원인이 없다")
	void keyServerError() throws IOException {
		GoogleIdTokenVerifier real = realVerifier(startKeyServer(503, "{}", false), Duration.ofSeconds(2));

		assertThatThrownBy(() -> real.verify(sign(googleKey, claims -> { })))
			.isInstanceOf(GoogleLoginException.class)
			.hasNoCause();
	}

	@Test
	@DisplayName("공개키 주소에 연결할 수 없으면 GoogleLoginException이다")
	void keyServerUnreachable() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		GoogleIdTokenVerifier real = realVerifier("http://127.0.0.1:" + closedPort + "/certs", Duration.ofSeconds(2));

		assertThatThrownBy(() -> real.verify(sign(googleKey, claims -> { })))
			.isInstanceOf(GoogleLoginException.class);
	}

	@Test
	@DisplayName("공개키 응답이 응답 제한 시간보다 늦으면 기다리지 않고 GoogleLoginException이다")
	void keyServerTimeout() throws IOException {
		GoogleIdTokenVerifier real = realVerifier(startKeyServer(200, "{\"keys\":[]}", true), Duration.ofMillis(200));
		long startedAt = System.nanoTime();

		assertThatThrownBy(() -> real.verify(sign(googleKey, claims -> { })))
			.isInstanceOf(GoogleLoginException.class);
		assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(3));
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "a.b.c", ""})
	@DisplayName("공개키 주소에 연결할 수 없어도 형식이 깨진 토큰은 키를 받기 전에 걸러져 빈 값이다")
	void malformedTokenWithoutKeyServer(String token) throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		GoogleIdTokenVerifier real = realVerifier("http://127.0.0.1:" + closedPort + "/certs", Duration.ofSeconds(2));

		assertThat(real.verify(token)).isEmpty();
	}

	/** 운영 생성자 그대로 만든다. 공개키 주소와 응답 제한만 테스트용으로 바꾼다. */
	private GoogleIdTokenVerifier realVerifier(String jwkSetUri, Duration readTimeout) {
		GoogleProperties properties = new GoogleProperties(CLIENT_ID, "test-secret",
			List.of("http://localhost:5173/auth/google/callback"), "https://oauth2.test/token", jwkSetUri,
			Duration.ofSeconds(2), readTimeout);
		return new GoogleIdTokenVerifier(properties, clock);
	}

	private String startKeyServer(int status, String body, boolean slow) throws IOException {
		keyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		keyServer.createContext("/certs", exchange -> {
			if (slow) {
				try {
					releaseKeyServer.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		keyServer.start();
		return "http://127.0.0.1:" + keyServer.getAddress().getPort() + "/certs";
	}

	/** 구글 ID 토큰과 같은 모양의 토큰. customizer로 클레임 하나씩 바꿔 실패 경우를 만든다. */
	private String sign(KeyPair key, Consumer<JwtClaimsSet.Builder> customizer) {
		Instant now = clock.instant();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer("accounts.google.com")
			.audience(List.of(CLIENT_ID))
			.subject("109876543210987654321")
			.claim("email", "user@gmail.com")
			.claim("email_verified", true)
			.claim("name", "홍길동")
			.claim("picture", "https://lh3.googleusercontent.com/a/sample")
			.issuedAt(now)
			.expiresAt(now.plus(Duration.ofHours(1)));
		customizer.accept(claims);

		RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey)key.getPublic())
			.privateKey((RSAPrivateKey)key.getPrivate())
			.keyID("test-key")
			.build();
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-key").build();
		return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	private static KeyPair newRsaKey() throws NoSuchAlgorithmException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

}
