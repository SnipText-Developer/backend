package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.sniptext.support.IntegrationTestSupport;

/** 명세 8~18번과 S2 27번의 스웨거 문서(/v3/api-docs). Docs 인터페이스의 설명이 실제 문서에 들어가는지 확인한다. */
class TemplateApiDocsTest extends IntegrationTestSupport {

	private static final String TEMPLATE_TAG = "내 템플릿";
	private static final String TRASH_TAG = "휴지통";

	@ParameterizedTest(name = "{0}번 {1} {2}")
	@CsvSource({
		"8, get, /api/v1/templates, 내 템플릿",
		"9, get, /api/v1/templates/{templateId}, 내 템플릿",
		"10, post, /api/v1/templates, 내 템플릿",
		"11, put, /api/v1/templates/{templateId}, 내 템플릿",
		"12, delete, /api/v1/templates/{templateId}, 내 템플릿",
		"13, get, /api/v1/templates/shortcuts/availability, 내 템플릿",
		"14, post, /api/v1/templates/{templateId}/usages, 내 템플릿",
		"15, get, /api/v1/trash/templates, 휴지통",
		"16, post, /api/v1/trash/templates/{templateId}/restore, 휴지통",
		"17, delete, /api/v1/trash/templates/{templateId}, 휴지통",
		"18, delete, /api/v1/trash/templates, 휴지통",
		"27, get, /api/v1/templates/sync, 내 템플릿"
	})
	@DisplayName("8~18번·27번은 화면 단위 태그에 들어 있고 summary가 있으며 로그인 회원 번호를 요청 파라미터로 노출하지 않는다")
	void operationHasTagSummaryAndHidesLoginUser(int number, String method, String path, String tag)
		throws Exception {
		Map<String, Object> operation = apiDocs().read("$.paths['" + path + "']." + method);

		assertThat(operation).as("%d번 문서", number).isNotNull();
		assertThat((List<String>)operation.get("tags")).containsExactly(tag);
		assertThat((String)operation.get("summary")).isNotBlank();
		assertThat(parameterNames(operation)).doesNotContain("userId");
	}

	@Test
	@DisplayName("두 태그는 한 줄 설명과 함께 문서의 태그 목록에 있다")
	void tagsHaveDescriptions() throws Exception {
		DocumentContext docs = apiDocs();

		for (String tag : List.of(TEMPLATE_TAG, TRASH_TAG)) {
			List<String> descriptions = docs.read("$.tags[?(@.name == '" + tag + "')].description");
			assertThat(descriptions).as(tag).singleElement().asString().isNotBlank();
		}
	}

	@Test
	@DisplayName("10번 생성은 실제 응답과 같은 201로 표시되고 공통 응답 스키마가 남아 있다")
	void createShowsCreatedWithSchema() throws Exception {
		Map<String, Map<String, Object>> responses = apiDocs().read("$.paths['/api/v1/templates'].post.responses");

		assertThat(responses).containsOnlyKeys("201");
		List<String> schemaRefs = JsonPath.read(responses.get("201"), "$.content.*.schema.$ref");
		assertThat(schemaRefs).isNotEmpty()
			.allSatisfy(ref -> assertThat(ref).endsWith("ApiResponseTemplateCreateResponse"));
	}

	@Test
	@DisplayName("13번·15번·27번의 쿼리 파라미터는 설명과 함께 노출된다")
	void queryParametersHaveDescriptions() throws Exception {
		DocumentContext docs = apiDocs();

		assertThat(parameterDescriptions(docs, "/api/v1/templates/shortcuts/availability"))
			.containsOnlyKeys("shortcut", "excludeTemplateId");
		assertThat(parameterDescriptions(docs, "/api/v1/trash/templates"))
			.containsOnlyKeys("cursor", "size");
		assertThat(parameterDescriptions(docs, "/api/v1/templates/sync"))
			.containsOnlyKeys("version");
	}

	@Test
	@DisplayName("15번 요청 객체로 묶은 size는 쿼리 파라미터로 펼쳐지고 기본값·범위가 표시된다")
	void trashSizeShowsDefaultAndRange() throws Exception {
		List<Map<String, Object>> sizes = apiDocs().read(
			"$.paths['/api/v1/trash/templates'].get.parameters[?(@.name == 'size')]");

		assertThat(sizes).singleElement().satisfies(size -> {
			assertThat(size).containsEntry("in", "query");
			assertThat((Map<String, Object>)size.get("schema"))
				.containsEntry("type", "integer")
				.containsEntry("minimum", 1)
				.containsEntry("maximum", 50)
				.containsEntry("default", 20);
		});
	}

	private DocumentContext apiDocs() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		return JsonPath.parse(json);
	}

	private List<String> parameterNames(Map<String, Object> operation) {
		if (!operation.containsKey("parameters")) {
			return List.of();
		}
		return JsonPath.read(operation, "$.parameters[*].name");
	}

	/** GET 요청의 파라미터 이름 → 설명. 설명이 비어 있으면 실패한다. */
	private Map<String, String> parameterDescriptions(DocumentContext docs, String path) {
		List<Map<String, Object>> parameters = docs.read("$.paths['" + path + "'].get.parameters");
		assertThat(parameters).allSatisfy(parameter ->
			assertThat((String)parameter.get("description")).as("%s 설명", parameter.get("name")).isNotBlank());

		return parameters.stream()
			.collect(Collectors.toMap(p -> (String)p.get("name"), p -> (String)p.get("description")));
	}

}
