package com.sniptext.domain.gallery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.gallery.entity.GalleryCategory;
import com.sniptext.domain.gallery.entity.GalleryTemplate;
import com.sniptext.domain.gallery.repository.GalleryTemplateRepository;
import com.sniptext.fixture.GalleryTemplateFixture;
import com.sniptext.support.IntegrationTestSupport;

/** 명세 19·20번. 응답 필드, 캐시 헤더, 토큰과 상관없이 같은 응답인지 실제 필터까지 거쳐 확인한다. */
@Transactional
class GalleryControllerTest extends IntegrationTestSupport {

	private static final String LIST_PATH = "/api/v1/gallery/templates";
	private static final String PUBLIC_CACHE = "public, max-age=300";
	private static final String NO_STORE = "no-store";
	private static final List<String> FIELD_ORDER = List.of(
		"galleryTemplateId", "category", "categoryName", "title", "shortcut", "content", "variables");

	@Autowired
	private GalleryTemplateRepository galleryTemplateRepository;

	private GalleryTemplate quote;
	private GalleryTemplate thanks;
	private GalleryTemplate hidden;

	@BeforeEach
	void setUp() {
		thanks = galleryTemplateRepository.saveAndFlush(GalleryTemplateFixture.galleryTemplate(
			GalleryCategory.CUSTOMER_SERVICE, "문의 접수 확인", "/문의접수", "문의가 접수되었습니다.", 20, true));
		hidden = galleryTemplateRepository.saveAndFlush(GalleryTemplateFixture.unpublished(1));
		quote = galleryTemplateRepository.saveAndFlush(GalleryTemplateFixture.published(10));
	}

	@Test
	@DisplayName("목록: 공개만 노출 순서대로, 개수·카테고리 이름·변수를 담고 공개 캐시 헤더가 붙는다")
	void list() throws Exception {
		MvcResult result = mockMvc.perform(get(LIST_PATH))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, PUBLIC_CACHE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("GALLERY200_2"))
			.andExpect(jsonPath("$.message").value("갤러리 조회에 성공했습니다."))
			.andExpect(jsonPath("$.result.totalCount").value(2))
			.andExpect(jsonPath("$.result.templates", hasSize(2)))
			.andExpect(jsonPath("$.result.templates[0].galleryTemplateId").value(quote.getId()))
			.andExpect(jsonPath("$.result.templates[0].category").value("WORK"))
			.andExpect(jsonPath("$.result.templates[0].categoryName").value("업무"))
			.andExpect(jsonPath("$.result.templates[0].title").value(GalleryTemplateFixture.TITLE))
			.andExpect(jsonPath("$.result.templates[0].shortcut").value(GalleryTemplateFixture.SHORTCUT))
			.andExpect(jsonPath("$.result.templates[0].content").value(GalleryTemplateFixture.CONTENT))
			.andExpect(jsonPath("$.result.templates[0].variables", contains("고객명", "작업범위", "금액", "기간")))
			.andExpect(jsonPath("$.result.templates[1].galleryTemplateId").value(thanks.getId()))
			.andExpect(jsonPath("$.result.templates[1].category").value("CUSTOMER_SERVICE"))
			.andExpect(jsonPath("$.result.templates[1].categoryName").value("고객 응대"))
			.andExpect(jsonPath("$.result.templates[1].variables", hasSize(0)))
			.andReturn();

		String body = body(result);
		assertThat(body.indexOf("\"totalCount\"")).isLessThan(body.indexOf("\"templates\""));
		assertFieldOrder(body.substring(body.indexOf("\"templates\"")));
	}

	@Test
	@DisplayName("상세: 공개 템플릿의 모든 필드를 명세 순서대로 담고 공개 캐시 헤더가 붙는다")
	void detail() throws Exception {
		MvcResult result = mockMvc.perform(get(LIST_PATH + "/{id}", quote.getId()))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, PUBLIC_CACHE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("GALLERY200_3"))
			.andExpect(jsonPath("$.message").value("갤러리 템플릿 조회에 성공했습니다."))
			.andExpect(jsonPath("$.result.galleryTemplateId").value(quote.getId()))
			.andExpect(jsonPath("$.result.category").value("WORK"))
			.andExpect(jsonPath("$.result.categoryName").value("업무"))
			.andExpect(jsonPath("$.result.title").value(GalleryTemplateFixture.TITLE))
			.andExpect(jsonPath("$.result.shortcut").value(GalleryTemplateFixture.SHORTCUT))
			.andExpect(jsonPath("$.result.content").value(GalleryTemplateFixture.CONTENT))
			.andExpect(jsonPath("$.result.variables", contains("고객명", "작업범위", "금액", "기간")))
			.andExpect(jsonPath("$.result.length()").value(FIELD_ORDER.size()))
			.andReturn();

		assertFieldOrder(body(result));
	}

	@Test
	@DisplayName("상세: 비공개 템플릿이면 GALLERY404_1, 캐시 금지")
	void hiddenDetail() throws Exception {
		expectNotFound(hidden.getId());
	}

	@Test
	@DisplayName("상세: 없는 ID면 GALLERY404_1, 캐시 금지")
	void missingDetail() throws Exception {
		expectNotFound(quote.getId() + 1_000);
	}

	@Test
	@DisplayName("상세: ID가 숫자가 아니면 COMMON400_1, 캐시 금지")
	void invalidId() throws Exception {
		mockMvc.perform(get(LIST_PATH + "/abc"))
			.andExpect(status().isBadRequest())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("COMMON400_1"))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"list", "detail"})
	@DisplayName("토큰 없음·유효 토큰·잘못된 토큰 모두 200이고 본문이 완전히 같다")
	void sameResponseRegardlessOfToken(String target) throws Exception {
		String path = target.equals("list") ? LIST_PATH : LIST_PATH + "/" + quote.getId();

		String anonymous = okBody(get(path));
		String authenticated = okBody(get(path).header(HttpHeaders.AUTHORIZATION, bearer(1L)));
		String invalidToken = okBody(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer abc"));

		assertThat(authenticated).isEqualTo(anonymous);
		assertThat(invalidToken).isEqualTo(anonymous);
	}

	private void expectNotFound(Long id) throws Exception {
		mockMvc.perform(get(LIST_PATH + "/{id}", id))
			.andExpect(status().isNotFound())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("GALLERY404_1"))
			.andExpect(jsonPath("$.message").value("갤러리 템플릿을 찾을 수 없어요."))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	private String okBody(MockHttpServletRequestBuilder request) throws Exception {
		return body(mockMvc.perform(request)
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, PUBLIC_CACHE))
			.andReturn());
	}

	private static String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	/** 첫 번째 템플릿 객체의 필드가 명세 순서대로 나오는지 본다 */
	private static void assertFieldOrder(String json) {
		List<Integer> positions = new ArrayList<>();
		for (String field : FIELD_ORDER) {
			int position = json.indexOf("\"" + field + "\"");
			assertThat(position).as(field).isNotNegative();
			positions.add(position);
		}
		assertThat(positions).isSorted();
	}

}
