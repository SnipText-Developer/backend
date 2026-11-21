package com.sniptext.global.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;

import com.sniptext.support.IntegrationTestSupport;

/**
 * 허용 출처는 build.gradle 테스트 환경 변수 CORS_ALLOWED_ORIGINS (https://sniptext.kr, http://localhost:3000).
 */
class CorsTest extends IntegrationTestSupport {

	private static final String DISALLOWED_ORIGIN = "https://evil.example.com";

	@ParameterizedTest
	@ValueSource(strings = {"https://sniptext.kr", "http://localhost:3000"})
	@DisplayName("허용 출처의 사전 요청은 로그인이 필요한 경로라도 토큰 없이 통과하고 자격 증명 허용 헤더가 붙는다")
	void preflightFromAllowedOrigin(String origin) throws Exception {
		mockMvc.perform(options("/api/v1/test/ping")
				.header(HttpHeaders.ORIGIN, origin)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
	}

	@Test
	@DisplayName("허용 출처의 인증된 요청에 허용 헤더가 붙는다")
	void actualRequestFromAllowedOrigin() throws Exception {
		mockMvc.perform(get("/api/v1/test/ping")
				.header(HttpHeaders.ORIGIN, "https://sniptext.kr")
				.header(HttpHeaders.AUTHORIZATION, bearer(1L)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://sniptext.kr"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	@DisplayName("401 에러 응답에도 허용 헤더가 붙어 화면이 에러 코드를 읽을 수 있다")
	void errorResponseHasCorsHeaders() throws Exception {
		mockMvc.perform(get("/api/v1/test/ping")
				.header(HttpHeaders.ORIGIN, "http://localhost:3000"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
			.andExpect(jsonPath("$.code").value("COMMON401_1"));
	}

	@Test
	@DisplayName("갤러리 공개 캐시 응답은 출처별로 캐시되도록 Vary에 Origin이 들어간다")
	void galleryResponseVariesByOrigin() throws Exception {
		mockMvc.perform(get("/api/v1/gallery/test/ping")
				.header(HttpHeaders.ORIGIN, "https://sniptext.kr"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=300"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://sniptext.kr"))
			.andExpect(header().stringValues(HttpHeaders.VARY, hasItem("Origin")));
	}

	@Test
	@DisplayName("허용하지 않은 출처의 사전 요청은 403이고 허용 헤더가 붙지 않는다")
	void preflightFromDisallowedOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/test/ping")
				.header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	@DisplayName("허용하지 않은 출처의 실제 요청은 403이고 허용 헤더가 붙지 않는다")
	void actualRequestFromDisallowedOrigin() throws Exception {
		mockMvc.perform(get("/api/v1/gallery/test/ping")
				.header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	@DisplayName("Origin이 없는 요청(헬스 체크 등)은 그대로 통과하고 허용 헤더가 붙지 않는다")
	void requestWithoutOrigin() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

}
