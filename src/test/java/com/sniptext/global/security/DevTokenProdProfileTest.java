package com.sniptext.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sniptext.TestcontainersConfiguration;

// 운영 설정 파일을 그대로 읽는다. DB 접속은 테스트컨테이너가 덮어쓰므로 환경 변수는 아무 값이나 넣는다.
// 운영에서는 가짜 언어 모델로 기동할 수 없으므로 anthropic으로 둔다 (호출하지 않으니 키·모델은 아무 값이나).
@SpringBootTest(properties = {"DB_URL=jdbc:mysql://unused", "DB_USERNAME=unused", "DB_PASSWORD=unused",
	"AI_PROVIDER=anthropic", "AI_API_KEY=unused", "AI_MODEL=unused"})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class DevTokenProdProfileTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("운영 프로필에서는 개발용 임시 토큰 경로가 없어 404 COMMON404_1이다")
	void devTokenNotFoundInProd() throws Exception {
		mockMvc.perform(post("/api/v1/auth/dev-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":1}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("COMMON404_1"));
	}

	@Test
	@DisplayName("운영 프로필에서는 시드 회원이 들어가지 않는다")
	void noSeedUsersInProd() {
		Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email LIKE '%@sniptext.local'",
			Long.class);
		assertThat(count).isZero();
	}

}
