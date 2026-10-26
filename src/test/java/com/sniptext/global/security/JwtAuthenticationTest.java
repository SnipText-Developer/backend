package com.sniptext.global.security;

import static com.sniptext.support.TestTokens.sign;
import static com.sniptext.support.TestTokens.tamperSignature;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.sniptext.global.config.TimeConfig;
import com.sniptext.support.IntegrationTestSupport;

@ExtendWith(OutputCaptureExtension.class)
class JwtAuthenticationTest extends IntegrationTestSupport {

	private static final String PROTECTED_PATH = "/api/v1/test/login-user";

	@Value("${sniptext.jwt.secret}")
	private String secret;

	@Test
	@DisplayName("유효한 접근 토큰이면 @LoginUser에 회원 번호가 들어온다")
	void validToken() throws Exception {
		mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, bearer(7L)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value(7));
	}

	@Test
	@DisplayName("토큰이 없거나 Bearer 접두어가 없으면 COMMON401_1")
	void missingToken() throws Exception {
		expectUnauthorized(mockMvc.perform(get(PROTECTED_PATH)), "COMMON401_1");
		expectUnauthorized(perform("Basic dXNlcjpwYXNz"), "COMMON401_1");
	}

	@Test
	@DisplayName("만료된 토큰은 COMMON401_1 (재발급 대상)")
	void expired() throws Exception {
		expectUnauthorized(perform("Bearer " + expiredToken()), "COMMON401_1");
	}

	@Test
	@DisplayName("서명이 변조됐거나 종류(typ)가 다른 토큰은 COMMON401_2")
	void tamperedOrOtherType() throws Exception {
		String tampered = tamperSignature(bearer(7L));
		String signupToken = sign(secret, "SIGNUP", "7", Instant.now().plus(Duration.ofMinutes(30)));

		expectUnauthorized(perform(tampered), "COMMON401_2");
		expectUnauthorized(perform("Bearer " + signupToken), "COMMON401_2");
	}

	@ParameterizedTest
	@ValueSource(strings = {"Bearer abc", "Bearer ", "Bearer", "bearer abc", "Bearer a.b.c",
		"Bearer qwertyuiopasdfghjklzxcvbnmqwertyuiopasdfghjklzxcvbnmqwertyuiopasdfghjklzxcvbnm1234567890",
		"Bearer 한글!@#.토큰$%^.깨짐&*()"})
	@DisplayName("Bearer 뒤가 비었거나 깨진 값이면 500이 아니라 COMMON401_2")
	void malformedToken(String authorization) throws Exception {
		expectUnauthorized(perform(authorization), "COMMON401_2");
	}

	@Test
	@DisplayName("공개 경로는 잘못된 토큰이 붙어도 401이 아니다")
	void permittedPathsIgnoreInvalidToken() throws Exception {
		mockMvc.perform(get("/api/v1/gallery/test/ping").header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/gallery/test/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/auth/test/ping").header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("인증 정보 없이 @LoginUser를 쓰면 COMMON401_1")
	void loginUserWithoutAuthentication() throws Exception {
		expectUnauthorized(mockMvc.perform(get("/api/v1/gallery/test/login-user")), "COMMON401_1");
	}

	@Test
	@DisplayName("인증 실패 경로의 로그에 토큰 원문과 서명 키가 남지 않는다")
	void noTokenInLogs(CapturedOutput output) throws Exception {
		String tampered = tamperSignature(bearer(7L));
		String expired = "Bearer " + expiredToken();
		List<String> headers = List.of(tampered, expired, "Bearer secret-looking-garbage-token-1234");

		for (String authorization : headers) {
			perform(authorization).andExpect(status().isUnauthorized());
		}

		for (String authorization : headers) {
			assertThat(output).doesNotContain(authorization.substring("Bearer ".length()));
		}
		assertThat(output).doesNotContain(secret);
	}

	// 같은 키로 1시간 전에 발급한 토큰 (컨텍스트를 새로 띄우지 않으려고 시계를 바꾼 발급 도구를 직접 만든다)
	private String expiredToken() {
		Clock pastClock = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), TimeConfig.KST);
		return new JwtTokenProvider(secret, pastClock).issueAccessToken(7L);
	}

	private ResultActions perform(String authorization) throws Exception {
		return mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, authorization));
	}

	private static void expectUnauthorized(ResultActions actions, String code) throws Exception {
		actions
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.result").isEmpty());
	}

}
