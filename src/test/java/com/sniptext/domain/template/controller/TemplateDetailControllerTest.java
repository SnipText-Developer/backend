package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.entity.TemplateContent;
import com.sniptext.domain.template.entity.TemplateTitle;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.user.entity.User;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/** 명세 9번. 본인 소유·휴지통 구분과 응답 형식을 실제 필터·MySQL까지 거쳐 확인한다. */
@Transactional
@Import(MutableClockConfiguration.class)
class TemplateDetailControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/templates/{templateId}";
	private static final String NO_STORE = "no-store";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 7, 10, 30);
	private static final String CONTENT = "{{금액}}원입니다.\n{{고객명}}님, {{금액}}원을 확인해주세요.";

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JsonMapper jsonMapper;

	private Long userId;
	private Long otherUserId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
		userId = saveUser("owner@sniptext.com");
		otherUserId = saveUser("other@sniptext.com");
	}

	@Test
	@DisplayName("내 템플릿을 조회하면 200, 명세 순서의 필드와 +09:00이 붙은 시각, 캐시 금지 헤더")
	void getTemplate() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/Quote"));
		template.edit(TemplateTitle.create("프로젝트 견적 안내"), Shortcut.create("/Quote"), TemplateContent.create(CONTENT),
			FIXED.plusHours(1));
		template.markUsed(FIXED.plusHours(2));
		flushAndClear();

		MvcResult result = getAs(userId, template.getId())
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TEMPLATE200_2"))
			.andExpect(jsonPath("$.message").value("템플릿 조회에 성공했습니다."))
			.andExpect(jsonPath("$.result.templateId").value(template.getId()))
			.andExpect(jsonPath("$.result.title").value("프로젝트 견적 안내"))
			.andExpect(jsonPath("$.result.shortcut").value("/Quote"))
			.andExpect(jsonPath("$.result.content").value(CONTENT))
			.andExpect(jsonPath("$.result.lastUsedAt").value("2026-10-07T12:30:00+09:00"))
			.andExpect(jsonPath("$.result.createdAt").value("2026-10-07T10:30:00+09:00"))
			.andExpect(jsonPath("$.result.editedAt").value("2026-10-07T11:30:00+09:00"))
			.andReturn();

		assertThat(jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.get("result").propertyNames())
			.containsExactly("templateId", "title", "shortcut", "content", "variables", "lastUsedAt", "createdAt",
				"editedAt");
	}

	@Test
	@DisplayName("변수 목록은 복호화한 본문에서 뽑아 첫 등장 순으로 중복 없이 내려준다")
	void variables() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.templateWithContent(userId, CONTENT));
		flushAndClear();

		getAs(userId, template.getId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.variables", contains("금액", "고객명")));
	}

	@Test
	@DisplayName("사용 이력이 없으면 lastUsedAt은 null이다")
	void neverUsed() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		flushAndClear();

		getAs(userId, template.getId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.lastUsedAt").value(nullValue()));
	}

	@Test
	@DisplayName("없는 템플릿이면 TEMPLATE404_1")
	void notFound() throws Exception {
		expectNotFound(getAs(userId, Long.MAX_VALUE));
	}

	@Test
	@DisplayName("다른 회원의 템플릿이면 TEMPLATE404_1")
	void otherUsersTemplate() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(otherUserId));

		expectNotFound(getAs(userId, template.getId()));
	}

	@Test
	@DisplayName("내 템플릿이 휴지통에 있으면 TEMPLATE404_2")
	void trashed() throws Exception {
		Template template = saveTrashed(userId);

		expectError(getAs(userId, template.getId()), 404, "TEMPLATE404_2", "이미 삭제된 템플릿이에요.");
	}

	@Test
	@DisplayName("다른 회원의 휴지통 템플릿이면 TEMPLATE404_1이다 (존재 여부를 노출하지 않는다)")
	void otherUsersTrashed() throws Exception {
		Template template = saveTrashed(otherUserId);

		expectNotFound(getAs(userId, template.getId()));
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1, 캐시 금지")
	void unauthenticated() throws Exception {
		expectError(mockMvc.perform(get(PATH, 1L)), 401, "COMMON401_1", "로그인이 필요해요.");
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "99999999999999999999"})
	@DisplayName("templateId가 Long으로 바뀌지 않으면 COMMON400_1")
	void invalidTemplateId(String templateId) throws Exception {
		expectError(mockMvc.perform(get("/api/v1/templates/" + templateId)
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))), 400, "COMMON400_1", "잘못된 요청입니다.");
	}

	private ResultActions getAs(Long userId, Long templateId) throws Exception {
		return mockMvc.perform(get(PATH, templateId).header(HttpHeaders.AUTHORIZATION, bearer(userId)));
	}

	private static void expectNotFound(ResultActions actions) throws Exception {
		expectError(actions, 404, "TEMPLATE404_1", "템플릿을 찾을 수 없어요.");
	}

	private static void expectError(ResultActions actions, int status, String code, String message) throws Exception {
		actions.andExpect(status().is(status))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	private Template saveTrashed(Long ownerId) {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(ownerId));
		template.moveToTrash(FIXED);
		flushAndClear();
		return template;
	}

	/** 1차 캐시를 비워 응답이 DB에서 다시 읽은(복호화한) 값으로 만들어지게 한다 */
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
