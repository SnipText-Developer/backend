package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.user.entity.User;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/** 명세 14번. 사용 기록은 last_used_at만 바꾸고 edited_at·목록 순서는 그대로 둔다. */
@Transactional
@Import(MutableClockConfiguration.class)
class TemplateUsageControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/templates/{templateId}/usages";
	private static final String NO_STORE = "no-store";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 11, 19, 15);
	private static final LocalDateTime LATER = FIXED.plusHours(1);
	private static final String LATER_TEXT = "2026-10-11T20:15:00+09:00";
	private static final String DASHBOARD_COPY = "{\"channel\": \"DASHBOARD_COPY\"}";

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
	@DisplayName("기록하면 200과 명세 순서의 필드를 +09:00 시각으로 돌려주고 캐시하지 않는다")
	void recordUsage() throws Exception {
		Template template = saveTemplate(userId, TemplateFixture.SHORTCUT);
		clock.setTo(LATER);

		MvcResult result = postAs(userId, template.getId(), DASHBOARD_COPY)
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TEMPLATE200_6"))
			.andExpect(jsonPath("$.message").value("사용 기록이 저장되었습니다."))
			.andExpect(jsonPath("$.result.templateId").value(template.getId()))
			.andExpect(jsonPath("$.result.lastUsedAt").value(LATER_TEXT))
			.andReturn();

		assertThat(jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.get("result").propertyNames())
			.containsExactly("templateId", "lastUsedAt");
	}

	@ParameterizedTest
	@ValueSource(strings = {"DASHBOARD_COPY", "POPUP_COPY", "EMAIL_INLINE"})
	@DisplayName("명세의 세 채널 모두 기록된다")
	void everyChannel(String channel) throws Exception {
		Template template = saveTemplate(userId, TemplateFixture.SHORTCUT);
		clock.setTo(LATER);

		postAs(userId, template.getId(), "{\"channel\": \"" + channel + "\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("TEMPLATE200_6"));

		assertThat(reload(template).getLastUsedAt()).isEqualTo(LATER);
	}

	@Test
	@DisplayName("last_used_at·updated_at은 지금 시각이 되고 edited_at·created_at·이름·단축어·본문은 그대로다")
	void onlyUsageColumnsChange() throws Exception {
		Template template = saveTemplate(userId, TemplateFixture.SHORTCUT);
		clock.setTo(LATER);

		postAs(userId, template.getId(), DASHBOARD_COPY).andExpect(status().isOk());

		Template found = reload(template);
		assertThat(found.getLastUsedAt()).isEqualTo(LATER);
		assertThat(found.getUpdatedAt()).isEqualTo(LATER);
		assertThat(found.getEditedAt()).isEqualTo(FIXED);
		assertThat(found.getCreatedAt()).isEqualTo(FIXED);
		assertThat(found.getTitle().getValue()).isEqualTo(TemplateFixture.TITLE);
		assertThat(found.getShortcut().getValue()).isEqualTo(TemplateFixture.SHORTCUT);
		assertThat(found.getContent().getValue()).isEqualTo(TemplateFixture.CONTENT);
	}

	@Test
	@DisplayName("두 번 기록하면 나중 시각으로 갱신된다")
	void recordTwice() throws Exception {
		Template template = saveTemplate(userId, TemplateFixture.SHORTCUT);
		clock.setTo(LATER);
		postAs(userId, template.getId(), DASHBOARD_COPY).andExpect(status().isOk());
		flushAndClear();
		LocalDateTime second = LATER.plusMinutes(30);
		clock.setTo(second);

		postAs(userId, template.getId(), "{\"channel\": \"POPUP_COPY\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.lastUsedAt").value("2026-10-11T20:45:00+09:00"));

		Template found = reload(template);
		assertThat(found.getLastUsedAt()).isEqualTo(second);
		assertThat(found.getUpdatedAt()).isEqualTo(second);
		assertThat(found.getEditedAt()).isEqualTo(FIXED);
	}

	@Test
	@DisplayName("사용 기록 후에도 내 템플릿 목록 순서는 바뀌지 않는다")
	void listOrderUnchanged() throws Exception {
		Template first = saveTemplate(userId, "/첫째");
		Template second = saveTemplate(userId, "/둘째");
		Template third = saveTemplate(userId, "/셋째");
		clock.setTo(LATER);

		postAs(userId, third.getId(), DASHBOARD_COPY).andExpect(status().isOk());
		flushAndClear();

		mockMvc.perform(get("/api/v1/templates").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.templates[*].templateId").value(
				contains(first.getId().intValue(), second.getId().intValue(), third.getId().intValue())));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{}",
		"{\"channel\": null}",
		"{\"channel\": \"\"}",
		"{\"channel\": \"   \"}",
		"{\"channel\": \"SLACK_COPY\"}",
		"{\"channel\": \"dashboard_copy\"}",
		"{\"channel\": \" DASHBOARD_COPY \"}",
		"{\"channel\": \"1\"}",
		"{\"channel\": 1}",
		"{\"channel\": 0}",
		"{\"channel\": true}"
	})
	@DisplayName("채널이 없거나 지원하지 않는 값이면 COMMON400_1이 아니라 TEMPLATE400_9이고 기록하지 않는다")
	void unsupportedChannel(String body) throws Exception {
		Template template = saveTemplate(userId, TemplateFixture.SHORTCUT);

		expectError(postAs(userId, template.getId(), body), 400, "TEMPLATE400_9", "지원하지 않는 사용 채널이에요.");

		assertThat(reload(template).getLastUsedAt()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "{\"channel\": ", "null", "[]", "{\"channel\": [\"DASHBOARD_COPY\"]}",
		"{\"channel\": {}}"})
	@DisplayName("본문이 없거나 JSON 형식·타입이 깨지면 전역 처리대로 COMMON400_1이고 기록하지 않는다")
	void unreadableBody(String body) throws Exception {
		Template template = saveTemplate(userId, TemplateFixture.SHORTCUT);

		expectError(postAs(userId, template.getId(), body), 400, "COMMON400_1", "잘못된 요청입니다.");

		assertThat(reload(template).getLastUsedAt()).isNull();
	}

	@Test
	@DisplayName("채널이 틀리고 템플릿도 없으면 형식 검증이 먼저라 TEMPLATE400_9")
	void channelCheckedBeforeOwnership() throws Exception {
		expectError(postAs(userId, Long.MAX_VALUE, "{\"channel\": \"SLACK_COPY\"}"), 400, "TEMPLATE400_9",
			"지원하지 않는 사용 채널이에요.");
	}

	@Test
	@DisplayName("없는 템플릿이면 TEMPLATE404_1")
	void notFound() throws Exception {
		expectNotFound(postAs(userId, Long.MAX_VALUE, DASHBOARD_COPY));
	}

	@Test
	@DisplayName("다른 회원의 템플릿이면 TEMPLATE404_1이고 기록하지 않는다")
	void otherUsersTemplate() throws Exception {
		Template template = saveTemplate(otherUserId, TemplateFixture.SHORTCUT);

		expectNotFound(postAs(userId, template.getId(), DASHBOARD_COPY));

		assertThat(reload(template).getLastUsedAt()).isNull();
	}

	@Test
	@DisplayName("내 템플릿이 휴지통에 있으면 TEMPLATE404_2이고 기록하지 않는다")
	void trashed() throws Exception {
		Template template = saveTrashed(userId);
		clock.setTo(LATER);

		expectError(postAs(userId, template.getId(), DASHBOARD_COPY), 404, "TEMPLATE404_2", "이미 삭제된 템플릿이에요.");

		Template found = reload(template);
		assertThat(found.getLastUsedAt()).isNull();
		assertThat(found.getUpdatedAt()).isEqualTo(FIXED);
	}

	@Test
	@DisplayName("다른 회원의 휴지통 템플릿이면 TEMPLATE404_1이다 (존재 여부를 노출하지 않는다)")
	void otherUsersTrashed() throws Exception {
		Template template = saveTrashed(otherUserId);

		expectNotFound(postAs(userId, template.getId(), DASHBOARD_COPY));
	}

	@Test
	@DisplayName("templateId가 숫자가 아니면 COMMON400_1")
	void nonNumericTemplateId() throws Exception {
		expectError(mockMvc.perform(post("/api/v1/templates/abc/usages")
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))
			.contentType(MediaType.APPLICATION_JSON)
			.content(DASHBOARD_COPY)), 400, "COMMON400_1", "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1, 캐시 금지")
	void unauthenticated() throws Exception {
		expectError(mockMvc.perform(post(PATH, 1L)
			.contentType(MediaType.APPLICATION_JSON)
			.content(DASHBOARD_COPY)), 401, "COMMON401_1", "로그인이 필요해요.");
	}

	private ResultActions postAs(Long userId, Long templateId, String body) throws Exception {
		return mockMvc.perform(post(PATH, templateId)
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
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

	private Template saveTemplate(Long ownerId, String shortcut) {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(ownerId, shortcut));
		flushAndClear();
		return template;
	}

	private Template saveTrashed(Long ownerId) {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(ownerId));
		template.moveToTrash(FIXED);
		flushAndClear();
		return template;
	}

	/** 쌓인 변경을 내보내고 1차 캐시를 비워 DB 값을 다시 읽는다 */
	private Template reload(Template template) {
		flushAndClear();
		return entityManager.find(Template.class, template.getId());
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
