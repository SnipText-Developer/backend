package com.sniptext.global.client.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/** 구글 대신 가짜 응답으로 코드 교환 규칙을 확인한다. ID 토큰 검증 규칙은 GoogleIdTokenVerifierTest에서 본다. */
class GoogleAuthCodeClientTest {

	private static final String TOKEN_URI = "https://oauth2.test/token";
	private static final String CLIENT_ID = "sniptext-web.apps.googleusercontent.com";
	private static final String CLIENT_SECRET = "secret-value-1234";
	private static final String REDIRECT_URI = "https://sniptext.io/auth/google/callback";
	private static final String CODE = "4/0AQSTgQ-secret-code";
	private static final String CODE_VERIFIER = "pkce-verifier-0123456789abcdefghijklmnopqrstuvwxyz";
	private static final String ID_TOKEN = "eyJhbGciOiJSUzI1NiJ9.payload.signature";
	private static final GoogleAccount ACCOUNT = new GoogleAccount(
		"109876543210987654321", "user@gmail.com", "홍길동", "https://lh3.googleusercontent.com/a/sample");

	private GoogleIdTokenVerifier idTokenVerifier;
	private MockRestServiceServer server;
	private GoogleAuthCodeClient client;
	private HttpServer slowServer;
	private final CountDownLatch releaseSlowServer = new CountDownLatch(1);

	@BeforeEach
	void setUp() {
		idTokenVerifier = mock(GoogleIdTokenVerifier.class);
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new GoogleAuthCodeClient(builder, properties(TOKEN_URI, Duration.ofSeconds(5)), idTokenVerifier);
	}

	@AfterEach
	void tearDown() {
		releaseSlowServer.countDown();
		if (slowServer != null) {
			slowServer.stop(0);
		}
	}

	@Test
	@DisplayName("토큰 엔드포인트에 grant_type·code·code_verifier·redirect_uri·client_id·client_secret을 폼으로 보낸다")
	void request() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("code", CODE);
		form.add("code_verifier", CODE_VERIFIER);
		form.add("redirect_uri", REDIRECT_URI);
		form.add("client_id", CLIENT_ID);
		form.add("client_secret", CLIENT_SECRET);
		server.expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(content().formData(form))
			.andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON));
		given(idTokenVerifier.verify(ID_TOKEN)).willReturn(Optional.of(ACCOUNT));

		client.exchange(CODE, CODE_VERIFIER, REDIRECT_URI);

		server.verify();
	}

	@Test
	@DisplayName("교환에 성공하면 받은 ID 토큰을 검증해 계정 정보를 돌려준다")
	void exchangeAndVerify() {
		respond(tokenResponse());
		given(idTokenVerifier.verify(ID_TOKEN)).willReturn(Optional.of(ACCOUNT));

		assertThat(client.exchange(CODE, CODE_VERIFIER, REDIRECT_URI)).contains(ACCOUNT);
	}

	@Test
	@DisplayName("받은 ID 토큰이 검증을 통과하지 못하면 빈 값이다")
	void idTokenRejected() {
		respond(tokenResponse());
		given(idTokenVerifier.verify(ID_TOKEN)).willReturn(Optional.empty());

		assertThat(client.exchange(CODE, CODE_VERIFIER, REDIRECT_URI)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://evil.example.com/callback", "https://sniptext.io/auth/google/callback/", ""})
	@DisplayName("redirectUri가 허용 목록과 정확히 같지 않으면 구글에 묻지 않고 빈 값이다")
	void redirectUriNotAllowed(String redirectUri) {
		assertThat(client.exchange(CODE, CODE_VERIFIER, redirectUri)).isEmpty();

		server.verify();
		verify(idTokenVerifier, never()).verify(anyString());
	}

	@Test
	@DisplayName("잘못된·만료·재사용 코드(400 invalid_grant)면 빈 값이다")
	void invalidGrant() {
		server.expect(requestTo(TOKEN_URI))
			.andRespond(withStatus(HttpStatusCode.valueOf(400)).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"invalid_grant\",\"error_description\":\"Bad Request\"}"));

		assertThat(client.exchange(CODE, CODE_VERIFIER, REDIRECT_URI)).isEmpty();
		verify(idTokenVerifier, never()).verify(anyString());
	}

	@ParameterizedTest
	@CsvSource({
		"400, invalid_request",
		"400, redirect_uri_mismatch",
		"401, invalid_client",
		"403, unauthorized_client",
		"500, internal_failure",
		"503, temporarily_unavailable"
	})
	@DisplayName("invalid_grant가 아닌 오류(우리 설정 오류·구글 장애)는 상태와 오류 종류만 담은 GoogleLoginException이다")
	void googleOrConfigurationError(int status, String error) {
		server.expect(requestTo(TOKEN_URI))
			.andRespond(withStatus(HttpStatusCode.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"%s\",\"error_description\":\"비밀-응답-본문\"}".formatted(error)));

		assertThatThrownBy(() -> client.exchange(CODE, CODE_VERIFIER, REDIRECT_URI))
			.isInstanceOf(GoogleLoginException.class)
			.hasMessageContaining(String.valueOf(status))
			.hasMessageContaining(error)
			.hasNoCause()
			.satisfies(e -> assertThat(e.getMessage()).doesNotContain("비밀-응답-본문", CODE, CLIENT_SECRET));
	}

	@Test
	@DisplayName("오류 응답 본문이 JSON이 아니어도 GoogleLoginException이다")
	void unreadableErrorBody() {
		server.expect(requestTo(TOKEN_URI))
			.andRespond(withStatus(HttpStatusCode.valueOf(502)).contentType(MediaType.TEXT_HTML).body("<html>"));

		assertThatThrownBy(() -> client.exchange(CODE, CODE_VERIFIER, REDIRECT_URI))
			.isInstanceOf(GoogleLoginException.class)
			.hasNoCause();
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"access_token\":\"a\"}", "{\"id_token\":\"\"}", "{}"})
	@DisplayName("성공 응답에 ID 토큰이 없으면 GoogleLoginException이다")
	void withoutIdToken(String body) {
		respond(body);

		assertThatThrownBy(() -> client.exchange(CODE, CODE_VERIFIER, REDIRECT_URI))
			.isInstanceOf(GoogleLoginException.class);
	}

	@Test
	@DisplayName("응답이 응답 제한 시간보다 늦으면 GoogleLoginException이고 원인이 없다")
	void timeout() throws IOException {
		slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		slowServer.createContext("/token", exchange -> {
			try {
				releaseSlowServer.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		slowServer.start();
		GoogleAuthCodeClient realClient = realClient(
			"http://127.0.0.1:" + slowServer.getAddress().getPort() + "/token", Duration.ofMillis(200));
		long startedAt = System.nanoTime();

		assertThatThrownBy(() -> realClient.exchange(CODE, CODE_VERIFIER, REDIRECT_URI))
			.isInstanceOf(GoogleLoginException.class)
			.hasNoCause();
		// 가짜 서버는 5초 뒤에야 빈 응답을 준다. 시간 제한이 없으면 그때 다른 이유로 실패하므로 걸린 시간도 본다
		assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(3));
	}

	@Test
	@DisplayName("연결할 수 없으면 GoogleLoginException이고 원인이 없다")
	void connectionRefused() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		GoogleAuthCodeClient realClient = realClient("http://127.0.0.1:" + closedPort + "/token", Duration.ofSeconds(2));

		assertThatThrownBy(() -> realClient.exchange(CODE, CODE_VERIFIER, REDIRECT_URI))
			.isInstanceOf(GoogleLoginException.class)
			.hasNoCause();
	}

	private void respond(String body) {
		server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
	}

	private static String tokenResponse() {
		return "{\"access_token\":\"ya29.a0\",\"expires_in\":3599,\"scope\":\"openid email profile\","
			+ "\"token_type\":\"Bearer\",\"id_token\":\"%s\"}".formatted(ID_TOKEN);
	}

	private GoogleAuthCodeClient realClient(String tokenUri, Duration readTimeout) {
		GoogleProperties properties = properties(tokenUri, readTimeout);
		return new GoogleAuthCodeClient(
			RestClient.builder().requestFactory(GoogleClientConfig.requestFactory(properties)), properties,
			idTokenVerifier);
	}

	private static GoogleProperties properties(String tokenUri, Duration readTimeout) {
		return new GoogleProperties(CLIENT_ID, CLIENT_SECRET, List.of(REDIRECT_URI), tokenUri, Duration.ofSeconds(2),
			readTimeout);
	}

}
