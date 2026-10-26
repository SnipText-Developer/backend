package com.sniptext.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sniptext.support.IntegrationTestSupport;

@ActiveProfiles("local")
class DevTokenLocalProfileTest extends IntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("시드 회원 2명이 들어간다: 1번은 플랜 지정 없음(FREE), 2번은 PRO")
	void seedUsers() {
		List<Map<String, Object>> users = jdbcTemplate.queryForList(
			"SELECT id, email, plan_override FROM users WHERE email LIKE '%@sniptext.local' ORDER BY id");

		assertThat(users).hasSize(2);
		assertThat(users.get(0)).containsEntry("id", 1L).containsEntry("plan_override", null);
		assertThat(users.get(1)).containsEntry("id", 2L).containsEntry("plan_override", "PRO");
	}

	@Test
	@DisplayName("임시 토큰을 발급받아 로그인이 필요한 경로를 부르면 그 회원 번호로 인증된다")
	void issueAndUse() throws Exception {
		String body = issue("{\"userId\":2}")
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("COMMON200_1"))
			.andExpect(jsonPath("$.message").value("개발용 토큰을 발급했어요."))
			.andReturn().getResponse().getContentAsString();
		String accessToken = JsonPath.read(body, "$.result.accessToken");

		mockMvc.perform(get("/api/v1/test/login-user").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value(2));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"userId\":null}", "{\"userId\":0}", "{\"userId\":-1}"})
	@DisplayName("userId가 없거나 1보다 작으면 COMMON400_1이다")
	void invalidUserId(String requestBody) throws Exception {
		issue(requestBody)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON400_1"))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	@Test
	@DisplayName("스웨거 문서에 Bearer 인증 방식이 들어간다")
	void swaggerBearerScheme() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
			.andExpect(jsonPath("$.security[0].bearerAuth").isArray());
	}

	private ResultActions issue(String requestBody) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/dev-token")
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody));
	}

}
