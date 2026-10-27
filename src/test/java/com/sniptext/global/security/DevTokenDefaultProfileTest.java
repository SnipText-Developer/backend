package com.sniptext.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sniptext.support.IntegrationTestSupport;

/** 프로필을 지정하지 않은 서버. "!prod"였다면 여기서 임시 토큰이 열린다. */
class DevTokenDefaultProfileTest extends IntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("프로필 지정이 없으면 개발용 임시 토큰 경로가 없어 404 COMMON404_1이다")
	void devTokenNotFoundWithoutProfile() throws Exception {
		mockMvc.perform(post("/api/v1/auth/dev-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":1}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("COMMON404_1"));
	}

	@Test
	@DisplayName("프로필 지정이 없으면 시드 회원이 들어가지 않는다")
	void noSeedUsersWithoutProfile() {
		Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email LIKE '%@sniptext.local'",
			Long.class);
		assertThat(count).isZero();
	}

}
