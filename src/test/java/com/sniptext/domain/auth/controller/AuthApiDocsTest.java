package com.sniptext.domain.auth.controller;

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

/** 명세 1~4번의 스웨거 문서(/v3/api-docs). Docs 인터페이스의 설명이 실제 문서에 들어가는지 확인한다. */
class AuthApiDocsTest extends IntegrationTestSupport {

	private static final String AUTH_TAG = "인증";

	@ParameterizedTest(name = "{0}번 {1}")
	@CsvSource({
		"1, /api/v1/auth/login",
		"2, /api/v1/auth/signup",
		"3, /api/v1/auth/reissue",
		"4, /api/v1/auth/logout"
	})
	@DisplayName("1~4번은 인증 태그에 summary가 있고, 접근 토큰 없이 부르므로 전역 Bearer 요구를 지워 자물쇠 없이 표시된다")
	void operationHasTagSummaryAndNoSecurity(int number, String path) throws Exception {
		Map<String, Object> operation = apiDocs().read("$.paths['" + path + "'].post");

		assertThat(operation).as("%d번 문서", number).isNotNull();
		assertThat((List<String>)operation.get("tags")).containsExactly(AUTH_TAG);
		assertThat((String)operation.get("summary")).isNotBlank();
		assertThat((String)operation.get("description")).isNotBlank();
		assertThat(operation).containsKey("security");
		assertThat((List<?>)operation.get("security")).isEmpty();
	}

	@Test
	@DisplayName("인증 태그는 한 줄 설명과 함께 문서의 태그 목록에 있다")
	void tagHasDescription() throws Exception {
		List<String> descriptions = apiDocs().read("$.tags[?(@.name == '" + AUTH_TAG + "')].description");

		assertThat(descriptions).singleElement().asString().isNotBlank();
	}

	@Test
	@DisplayName("2번 가입은 실제 응답과 같은 201로 표시되고 공통 응답 스키마가 남아 있다")
	void signUpShowsCreatedWithSchema() throws Exception {
		Map<String, Map<String, Object>> responses = apiDocs().read("$.paths['/api/v1/auth/signup'].post.responses");

		assertThat(responses).containsOnlyKeys("201");
		List<String> schemaRefs = JsonPath.read(responses.get("201"), "$.content.*.schema.$ref");
		assertThat(schemaRefs).isNotEmpty()
			.allSatisfy(ref -> assertThat(ref).endsWith("ApiResponseAuthSignUpResponse"));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({
		"AuthLoginRequest, code|codeVerifier|redirectUri|provider",
		"AuthSignUpRequest, signupToken|termsAgreed"
	})
	@DisplayName("1·2번 요청 본문 필드는 모두 필수이고 설명이 있다")
	void requestFieldsHaveDescriptions(String schema, String fields) throws Exception {
		DocumentContext docs = apiDocs();
		Map<String, Map<String, Object>> properties = docs.read("$.components.schemas." + schema + ".properties");
		List<String> required = docs.read("$.components.schemas." + schema + ".required");
		List<String> expected = List.of(fields.split("\\|"));

		assertThat(properties).containsOnlyKeys(expected);
		assertThat(required).containsExactlyInAnyOrderElementsOf(expected);
		assertThat(properties.values()).allSatisfy(property ->
			assertThat((String)property.get("description")).isNotBlank());
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"/api/v1/auth/reissue", "/api/v1/auth/logout"})
	@DisplayName("3·4번은 재발급 토큰을 설명이 있는 쿠키 파라미터로만 받고 서블릿 응답 객체는 노출하지 않는다")
	void cookieParameterOnly(String path) throws Exception {
		List<Map<String, Object>> parameters = apiDocs().read("$.paths['" + path + "'].post.parameters");

		assertThat(parameters).singleElement().satisfies(parameter -> {
			assertThat(parameter).containsEntry("name", "refreshToken").containsEntry("in", "cookie");
			assertThat((String)parameter.get("description")).isNotBlank();
		});
	}

	private DocumentContext apiDocs() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		return JsonPath.parse(json);
	}

}
