package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.user.entity.User;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/** 명세 13번. 형식 오류·예약어·중복도 에러가 아니라 200 + reason이다. */
@Transactional
class TemplateShortcutAvailabilityControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/templates/shortcuts/availability";
	private static final String NO_STORE = "no-store";
	private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 10, 7, 10, 30);

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JsonMapper jsonMapper;

	private Long userId;
	private Long otherUserId;

	@BeforeEach
	void setUp() {
		userId = saveUser("owner@sniptext.com");
		otherUserId = saveUser("other@sniptext.com");
	}

	@Test
	@DisplayName("쓰지 않는 단축어면 200, available true, reason null을 명세 순서의 필드로 돌려주고 캐시하지 않는다")
	void available() throws Exception {
		MvcResult result = check(request("/견적서"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TEMPLATE200_5"))
			.andExpect(jsonPath("$.message").value("단축어 확인에 성공했습니다."))
			.andExpect(jsonPath("$.result.shortcut").value("/견적서"))
			.andExpect(jsonPath("$.result.available").value(true))
			.andExpect(jsonPath("$.result.reason").isEmpty())
			.andReturn();

		assertThat(jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.get("result").propertyNames())
			.containsExactly("shortcut", "available", "reason");
	}

	@Test
	@DisplayName("명세 예시처럼 인코딩한 주소로 보내도 디코딩해 판정한다")
	void encodedQuery() throws Exception {
		saveTemplate(userId, "/견적");

		expectUnavailable(check(get(URI.create(PATH + "?shortcut=%2F%EA%B2%AC%EC%A0%81"))
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))), "/견적", "DUPLICATED");
	}

	@Test
	@DisplayName("내 활성 템플릿이 쓰는 단축어면 DUPLICATED")
	void duplicated() throws Exception {
		saveTemplate(userId, "/견적");

		expectUnavailable(check(request("/견적")), "/견적", "DUPLICATED");
	}

	@Test
	@DisplayName("대소문자만 달라도 DUPLICATED이고, 응답 단축어는 입력 대소문자 그대로다")
	void duplicatedIgnoringCase() throws Exception {
		saveTemplate(userId, "/Quote");

		expectUnavailable(check(request("/QUOTE")), "/QUOTE", "DUPLICATED");
	}

	@Test
	@DisplayName("맥 자모 분리형(NFD)을 인코딩해 보내도 완성형과 같게 판정하고, 응답 단축어는 완성형이다")
	void nfdSameAsNfc() throws Exception {
		saveTemplate(userId, "/견적");
		String nfd = Normalizer.normalize("/견적", Normalizer.Form.NFD);
		assertThat(nfd).isNotEqualTo("/견적");

		expectUnavailable(check(get(PATH + "?shortcut={shortcut}", nfd)
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))), "/견적", "DUPLICATED");
	}

	@Test
	@DisplayName("자모 분리형으로 쓰면 11자를 넘는 단축어도 완성형으로 합친 뒤 형식을 본다")
	void nfdNormalizedBeforeFormat() throws Exception {
		String nfd = Normalizer.normalize("/견적요청서작성안내", Normalizer.Form.NFD);
		assertThat(nfd.length()).isGreaterThan(11);

		expectAvailable(check(request(nfd)), "/견적요청서작성안내");
	}

	@Test
	@DisplayName("휴지통 템플릿의 단축어는 사용 가능이다")
	void trashedShortcutAvailable() throws Exception {
		saveTrashed(userId, "/견적");

		expectAvailable(check(request("/견적")), "/견적");
	}

	@Test
	@DisplayName("다른 회원이 쓰는 같은 단축어는 중복이 아니다")
	void otherUsersShortcutAvailable() throws Exception {
		saveTemplate(otherUserId, "/견적");

		expectAvailable(check(request("/견적")), "/견적");
	}

	@Test
	@DisplayName("excludeTemplateId로 편집 중인 자기 자신을 빼면 사용 가능이다")
	void excludeSelf() throws Exception {
		Template self = saveTemplate(userId, "/견적");

		expectAvailable(check(request("/견적").param("excludeTemplateId", self.getId().toString())), "/견적");
	}

	@Test
	@DisplayName("자기 자신이 아닌 내 템플릿 ID를 빼면 같은 단축어를 쓰는 템플릿은 그대로 남아 DUPLICATED")
	void excludeAnotherTemplate() throws Exception {
		saveTemplate(userId, "/견적");
		Template another = saveTemplate(userId, "/안내");

		expectUnavailable(check(request("/견적").param("excludeTemplateId", another.getId().toString())),
			"/견적", "DUPLICATED");
	}

	@Test
	@DisplayName("다른 회원·휴지통·없는 템플릿 ID를 빼도 에러 없이 그 ID만 빼고 판정한다")
	void excludeForeignOrTrashedOrMissing() throws Exception {
		saveTemplate(userId, "/견적");
		Template others = saveTemplate(otherUserId, "/견적");
		Template trashed = saveTrashed(userId, "/견적");

		for (Long excluded : new Long[] {others.getId(), trashed.getId(), Long.MAX_VALUE}) {
			expectUnavailable(check(request("/견적").param("excludeTemplateId", excluded.toString())),
				"/견적", "DUPLICATED");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "/", "/a", "/견적요청서작성안내문서", "견적", "/견 적", "   ", "/견적!"})
	@DisplayName("비었거나 \"/\"만 있거나 형식이 틀리면 200 + INVALID_FORMAT")
	void invalidFormat(String shortcut) throws Exception {
		expectUnavailable(check(request(shortcut)), shortcut, "INVALID_FORMAT");
	}

	@Test
	@DisplayName("예약어 /톤교정은 200 + RESERVED")
	void reserved() throws Exception {
		expectUnavailable(check(request("/톤교정")), "/톤교정", "RESERVED");
	}

	@Test
	@DisplayName("excludeTemplateId가 빈 값이면 없는 것으로 보고 판정한다")
	void emptyExcludeTemplateId() throws Exception {
		saveTemplate(userId, "/견적");

		expectUnavailable(check(request("/견적").param("excludeTemplateId", "")), "/견적", "DUPLICATED");
	}

	@Test
	@DisplayName("shortcut 파라미터가 없으면 COMMON400_1, 캐시 금지")
	void missingShortcut() throws Exception {
		expectBadRequest(check(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(userId))));
		expectBadRequest(check(get(PATH).param("excludeTemplateId", "1")
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))));
	}

	@Test
	@DisplayName("excludeTemplateId가 숫자가 아니면 COMMON400_1")
	void nonNumericExcludeTemplateId() throws Exception {
		expectBadRequest(check(request("/견적").param("excludeTemplateId", "abc")));
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1, 캐시 금지")
	void unauthenticated() throws Exception {
		expectError(check(get(PATH).param("shortcut", "/견적")), 401, "COMMON401_1", "로그인이 필요해요.");
	}

	private MockHttpServletRequestBuilder request(String shortcut) {
		return get(PATH).param("shortcut", shortcut).header(HttpHeaders.AUTHORIZATION, bearer(userId));
	}

	private ResultActions check(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request);
	}

	private static void expectAvailable(ResultActions actions, String shortcut) throws Exception {
		actions.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.code").value("TEMPLATE200_5"))
			.andExpect(jsonPath("$.result.shortcut").value(shortcut))
			.andExpect(jsonPath("$.result.available").value(true))
			.andExpect(jsonPath("$.result.reason").isEmpty());
	}

	private static void expectUnavailable(ResultActions actions, String shortcut, String reason) throws Exception {
		actions.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TEMPLATE200_5"))
			.andExpect(jsonPath("$.result.shortcut").value(shortcut))
			.andExpect(jsonPath("$.result.available").value(false))
			.andExpect(jsonPath("$.result.reason").value(reason));
	}

	private static void expectBadRequest(ResultActions actions) throws Exception {
		expectError(actions, 400, "COMMON400_1", "잘못된 요청입니다.");
	}

	private static void expectError(ResultActions actions, int status, String code, String message) throws Exception {
		actions.andExpect(status().is(status))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	private Template saveTemplate(Long ownerId, String shortcut) {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(ownerId, shortcut));
		flushAndClear();
		return template;
	}

	private Template saveTrashed(Long ownerId, String shortcut) {
		Template template = templateRepository.saveAndFlush(
			TemplateFixture.trashedTemplate(ownerId, shortcut, DELETED_AT));
		flushAndClear();
		return template;
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	private Long saveUser(String email) {
		User user = UserFixture.user(email);
		entityManager.persist(user);
		entityManager.flush();
		return user.getId();
	}

}
