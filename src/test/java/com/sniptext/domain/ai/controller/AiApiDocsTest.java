package com.sniptext.domain.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.sniptext.domain.ai.entity.ToneStyle;
import com.sniptext.support.IntegrationTestSupport;

/** 명세 22번의 스웨거 문서(/v3/api-docs). Docs 인터페이스와 요청 필드 설명이 실제 문서에 들어가는지 확인한다. */
class AiApiDocsTest extends IntegrationTestSupport {

	private static final String AI_TAG = "AI 톤 교정";
	private static final String PATH = "/api/v1/ai/tone-corrections";

	@Test
	@DisplayName("22번은 AI 톤 교정 태그에 들어 있고 summary가 있으며 로그인 회원 번호를 요청 파라미터로 노출하지 않는다")
	void operationHasTagSummaryAndHidesLoginUser() throws Exception {
		Map<String, Object> operation = apiDocs().read("$.paths['" + PATH + "'].post");

		assertThat((List<String>)operation.get("tags")).containsExactly(AI_TAG);
		assertThat((String)operation.get("summary")).isNotBlank();
		assertThat(operation).doesNotContainKey("parameters");
	}

	@Test
	@DisplayName("AI 톤 교정 태그는 한 줄 설명과 함께 문서의 태그 목록에 있다")
	void tagHasDescription() throws Exception {
		List<String> descriptions = apiDocs().read("$.tags[?(@.name == '" + AI_TAG + "')].description");

		assertThat(descriptions).singleElement().asString().isNotBlank();
	}

	@Test
	@DisplayName("요청 본문의 style은 문체 코드 6개를, text는 3000자 제한을 설명과 함께 보여준다")
	void requestFieldsHaveDescriptions() throws Exception {
		DocumentContext docs = apiDocs();
		Map<String, Map<String, Object>> properties = docs.read(
			"$.components.schemas.AiToneCorrectRequest.properties");
		List<String> required = docs.read("$.components.schemas.AiToneCorrectRequest.required");

		assertThat(properties).containsOnlyKeys("style", "text");
		assertThat(required).containsExactlyInAnyOrder("style", "text");
		assertThat(properties.values()).allSatisfy(property ->
			assertThat((String)property.get("description")).isNotBlank());
		assertThat((List<String>)properties.get("style").get("enum"))
			.containsExactlyElementsOf(Arrays.stream(ToneStyle.values()).map(Enum::name).toList());
		assertThat(properties.get("text")).containsEntry("maxLength", 3000);
	}

	private DocumentContext apiDocs() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		return JsonPath.parse(json);
	}

}
