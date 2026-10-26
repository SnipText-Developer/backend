package com.sniptext.global.security;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sniptext.TestcontainersConfiguration;

// 개발 서버 설정 파일을 그대로 읽는다. DB 접속은 테스트컨테이너가 덮어쓰므로 환경 변수는 아무 값이나 넣는다.
@SpringBootTest(properties = {"DB_URL=jdbc:mysql://unused", "DB_USERNAME=unused", "DB_PASSWORD=unused"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class DevTokenDevProfileTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("개발 서버 프로필에서는 개발용 임시 토큰 경로가 열려 있다")
	void devTokenOpenInDev() throws Exception {
		mockMvc.perform(post("/api/v1/auth/dev-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":1}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("COMMON200_1"))
			.andExpect(jsonPath("$.result.accessToken").isNotEmpty());
	}

}
