package com.sniptext.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import com.sniptext.support.IntegrationTestSupport;

class CacheControlFilterTest extends IntegrationTestSupport {

	@Test
	@DisplayName("갤러리 GET 응답은 public, max-age=300이고 시큐리티 기본 캐시 금지 헤더가 덮어쓰지 않는다")
	void galleryGetIsPublic() throws Exception {
		mockMvc.perform(get("/api/v1/gallery/test/ping"))
			.andExpect(status().isOk())
			.andExpect(header().stringValues("Cache-Control", "public, max-age=300"))
			.andExpect(header().doesNotExist("Pragma"))
			.andExpect(header().doesNotExist("Expires"));
	}

	@Test
	@WithMockUser
	@DisplayName("갤러리 밖의 응답과 갤러리 경로의 GET이 아닌 요청은 no-store다")
	void othersAreNoStore() throws Exception {
		mockMvc.perform(get("/api/v1/test/ping"))
			.andExpect(status().isOk())
			.andExpect(header().stringValues("Cache-Control", "no-store"))
			.andExpect(header().doesNotExist("Pragma"));
		mockMvc.perform(post("/api/v1/gallery/test/ping"))
			.andExpect(status().isOk())
			.andExpect(header().stringValues("Cache-Control", "no-store"));
	}

	@Test
	@DisplayName("인증 실패 응답도 no-store다")
	void unauthorizedIsNoStore() throws Exception {
		mockMvc.perform(get("/api/v1/test/ping"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().stringValues("Cache-Control", "no-store"));
	}

	@Test
	@DisplayName("갤러리 GET이라도 에러 응답은 no-store다 (CDN이 에러를 다른 사람에게 돌려주지 않게)")
	void galleryErrorIsNoStore() throws Exception {
		mockMvc.perform(get("/api/v1/gallery/test/no-such-path"))
			.andExpect(status().isNotFound())
			.andExpect(header().stringValues("Cache-Control", "no-store"));
	}

}
