package com.sniptext.domain.gallery.controller;

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

/** 명세 19~21번의 스웨거 문서(/v3/api-docs). Docs 인터페이스의 설명이 실제 문서에 들어가는지 확인한다. */
class GalleryApiDocsTest extends IntegrationTestSupport {

	private static final String GALLERY_TAG = "갤러리";

	@ParameterizedTest(name = "{0}번 {1} {2}")
	@CsvSource({
		"19, get, /api/v1/gallery/templates",
		"20, get, /api/v1/gallery/templates/{galleryTemplateId}",
		"21, post, /api/v1/gallery/templates/{galleryTemplateId}/save"
	})
	@DisplayName("19~21번은 갤러리 태그에 들어 있고 summary가 있으며 로그인 회원 번호를 요청 파라미터로 노출하지 않는다")
	void operationHasTagSummaryAndHidesLoginUser(int number, String method, String path) throws Exception {
		Map<String, Object> operation = apiDocs().read("$.paths['" + path + "']." + method);

		assertThat(operation).as("%d번 문서", number).isNotNull();
		assertThat((List<String>)operation.get("tags")).containsExactly(GALLERY_TAG);
		assertThat((String)operation.get("summary")).isNotBlank();
		assertThat(parameterNames(operation)).doesNotContain("userId");
	}

	@Test
	@DisplayName("갤러리 태그는 한 줄 설명과 함께 문서의 태그 목록에 있다")
	void tagHasDescription() throws Exception {
		List<String> descriptions = apiDocs().read("$.tags[?(@.name == '" + GALLERY_TAG + "')].description");

		assertThat(descriptions).singleElement().asString().isNotBlank();
	}

	@ParameterizedTest(name = "{0}번")
	@CsvSource({
		"19, /api/v1/gallery/templates",
		"20, /api/v1/gallery/templates/{galleryTemplateId}"
	})
	@DisplayName("로그인이 필요 없는 19·20번 조회는 전역 Bearer 요구를 지워 자물쇠 없이 표시된다")
	void publicReadsHaveNoSecurity(int number, String path) throws Exception {
		Map<String, Object> operation = apiDocs().read("$.paths['" + path + "'].get");

		assertThat(operation).as("%d번 security", number).containsKey("security");
		assertThat((List<?>)operation.get("security")).isEmpty();
	}

	@Test
	@DisplayName("21번 저장은 전역 Bearer 요구를 그대로 따른다")
	void saveKeepsGlobalSecurity() throws Exception {
		Map<String, Object> operation = apiDocs().read(
			"$.paths['/api/v1/gallery/templates/{galleryTemplateId}/save'].post");

		assertThat(operation).doesNotContainKey("security");
	}

	@Test
	@DisplayName("21번 저장은 실제 응답과 같은 201로 표시되고 공통 응답 스키마가 남아 있다")
	void saveShowsCreatedWithSchema() throws Exception {
		Map<String, Map<String, Object>> responses = apiDocs().read(
			"$.paths['/api/v1/gallery/templates/{galleryTemplateId}/save'].post.responses");

		assertThat(responses).containsOnlyKeys("201");
		List<String> schemaRefs = JsonPath.read(responses.get("201"), "$.content.*.schema.$ref");
		assertThat(schemaRefs).isNotEmpty()
			.allSatisfy(ref -> assertThat(ref).endsWith("ApiResponseGallerySaveResponse"));
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

}
