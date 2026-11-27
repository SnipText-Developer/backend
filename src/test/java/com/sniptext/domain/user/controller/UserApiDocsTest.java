package com.sniptext.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.sniptext.support.IntegrationTestSupport;

/** 명세 5·6번의 스웨거 문서(/v3/api-docs). Docs 인터페이스의 설명이 실제 문서에 들어가는지 확인한다. */
class UserApiDocsTest extends IntegrationTestSupport {

	private static final String USER_TAG = "회원";

	@ParameterizedTest(name = "{0}번 {1}")
	@CsvSource({
		"5, /api/v1/users/me",
		"6, /api/v1/users/me/usage"
	})
	@DisplayName("5·6번은 회원 태그에 summary가 있고, 로그인 회원 번호를 요청 파라미터로 노출하지 않으며 전역 Bearer 요구를 따른다")
	void operationHasTagSummaryAndHidesLoginUser(int number, String path) throws Exception {
		Map<String, Object> operation = apiDocs().read("$.paths['" + path + "'].get");

		assertThat(operation).as("%d번 문서", number).isNotNull();
		assertThat((List<String>)operation.get("tags")).containsExactly(USER_TAG);
		assertThat((String)operation.get("summary")).isNotBlank();
		assertThat(operation).doesNotContainKey("parameters").doesNotContainKey("security");
	}

	@Test
	@DisplayName("회원 태그는 한 줄 설명과 함께 문서의 태그 목록에 있다")
	void tagHasDescription() throws Exception {
		List<String> descriptions = apiDocs().read("$.tags[?(@.name == '" + USER_TAG + "')].description");

		assertThat(descriptions).singleElement().asString().isNotBlank();
	}

	private DocumentContext apiDocs() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		return JsonPath.parse(json);
	}

}
