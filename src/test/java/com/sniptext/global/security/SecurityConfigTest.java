package com.sniptext.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import com.sniptext.support.IntegrationTestSupport;

class SecurityConfigTest extends IntegrationTestSupport {

	@Test
	@DisplayName("토큰 없이 보호된 경로를 부르면 공통 형식의 COMMON401_1로 응답한다")
	void unauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/test/ping"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(header().doesNotExist("WWW-Authenticate"))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("COMMON401_1"))
			.andExpect(jsonPath("$.message").value("로그인이 필요해요."))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	@Test
	@WithMockUser
	@DisplayName("권한이 없으면 공통 형식의 COMMON403_1로 응답한다")
	void forbidden() throws Exception {
		mockMvc.perform(get("/api/v1/test/forbidden"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("COMMON403_1"))
			.andExpect(jsonPath("$.message").value("접근 권한이 없어요."))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	@Test
	@WithMockUser
	@DisplayName("인증된 사용자는 CSRF 토큰 없이 POST를 보낼 수 있고 세션 쿠키가 생기지 않는다")
	void csrfDisabledAndStateless() throws Exception {
		mockMvc.perform(post("/api/v1/test/ping"))
			.andExpect(status().isOk())
			.andExpect(cookie().doesNotExist("JSESSIONID"))
			.andExpect(jsonPath("$.code").value("TEST200_1"));
	}

	@Test
	@DisplayName("갤러리 GET과 인증 경로는 토큰 없이 부를 수 있다")
	void permittedPaths() throws Exception {
		mockMvc.perform(get("/api/v1/gallery/test/ping"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/auth/test/ping"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("갤러리 경로라도 GET이 아니면 인증이 필요하다")
	void galleryPostRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/gallery/test/ping"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("COMMON401_1"));
	}

	@Test
	@DisplayName("헬스 체크와 스웨거 문서는 토큰 없이 부를 수 있다")
	void healthAndSwagger() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk());
	}

}
