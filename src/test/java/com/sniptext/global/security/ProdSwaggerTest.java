package com.sniptext.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
class ProdSwaggerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("운영 프로필에서는 스웨거 경로가 허용 목록에서 빠져 토큰 없이 부르면 401이다")
	void swaggerBlockedInProd() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("COMMON401_1"));
		mockMvc.perform(get("/swagger-ui.html"))
			.andExpect(status().isUnauthorized());
	}

}
