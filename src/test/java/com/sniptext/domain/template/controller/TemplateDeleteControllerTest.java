package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.sniptext.global.policy.UsageLimits;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * 명세 12번. 삭제는 deleted_at만 지금 시각으로 바꿔 휴지통으로 옮긴다 (소프트 삭제).
 * 9·11·14번과 달리 내 휴지통 템플릿도 TEMPLATE404_1이다.
 * 클래스 트랜잭션 안에서는 서비스의 변경이 커밋되지 않으므로, 감사 기능이 updated_at을 채우도록 시계를 옮기기 전에 내보낸다.
 */
@Transactional
@Import(MutableClockConfiguration.class)
class TemplateDeleteControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/templates/{templateId}";
	private static final String NO_STORE = "no-store";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 11, 19, 15);
	private static final LocalDateTime USED_AT = FIXED.plusMinutes(10);
	private static final LocalDateTime LATER = FIXED.plusHours(1);
	private static final String LATER_TEXT = "2026-10-11T20:15:00+09:00";
	/** LATER + 30일. 계산하지 않고 명세 예시처럼 글자로 둔다 */
	private static final String PURGE_AT_TEXT = "2026-11-10T20:15:00+09:00";

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
	@DisplayName("삭제하면 200과 명세 순서의 필드를 +09:00 시각으로 돌려주고 캐시하지 않는다. 영구 삭제 예정은 30일 뒤다")
	void deleteTemplate() throws Exception {
		Template template = saveTemplate(userId, TemplateFixture.SHORTCUT);
		clock.setTo(LATER);

		MvcResult result = deleteAs(userId, template.getId())
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TEMPLATE200_4"))
			.andExpect(jsonPath("$.message").value("템플릿을 휴지통으로 옮겼습니다."))
			.andExpect(jsonPath("$.result.templateId").value(template.getId()))
			.andExpect(jsonPath("$.result.title").value(TemplateFixture.TITLE))
			.andExpect(jsonPath("$.result.deletedAt").value(LATER_TEXT))
			.andExpect(jsonPath("$.result.purgeAt").value(PURGE_AT_TEXT))
			.andReturn();

		assertThat(jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.get("result").propertyNames())
			.containsExactly("templateId", "title", "deletedAt", "purgeAt");
	}

	@Test
	@DisplayName("deleted_at·updated_at은 지금 시각이 되고 edited_at·last_used_at·created_at·이름·단축어·본문은 그대로, 단축어 점유가 풀린다")
	void onlyTrashColumnsChange() throws Exception {
		Template template = saveUsedTemplate(userId);
		clock.setTo(LATER);

		deleteAs(userId, template.getId()).andExpect(status().isOk());

		Template found = reload(template);
		assertThat(found.getDeletedAt()).isEqualTo(LATER);
		assertThat(found.getUpdatedAt()).isEqualTo(LATER);
		assertThat(found.getEditedAt()).isEqualTo(FIXED);
		assertThat(found.getLastUsedAt()).isEqualTo(USED_AT);
		assertThat(found.getCreatedAt()).isEqualTo(FIXED);
		assertThat(found.getTitle().getValue()).isEqualTo(TemplateFixture.TITLE);
		assertThat(found.getShortcut().getValue()).isEqualTo(TemplateFixture.SHORTCUT);
		assertThat(found.getContent().getValue()).isEqualTo(TemplateFixture.CONTENT);
		assertThat(found.getActiveShortcutKey()).isNull();
	}

	@Test
	@DisplayName("삭제한 템플릿은 휴지통 목록(15번)에 같은 시각으로 나오고 내 템플릿 목록(8번)에서 빠지며 활성 개수가 1 준다")
	void movesFromListToTrash() throws Exception {
		Template kept = saveTemplate(userId, "/남김");
		Template deleted = saveTemplate(userId, TemplateFixture.SHORTCUT);
		clock.setTo(LATER);

		deleteAs(userId, deleted.getId()).andExpect(status().isOk());
		flushAndClear();

		mockMvc.perform(get("/api/v1/trash/templates").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.totalCount").value(1))
			.andExpect(jsonPath("$.result.templates[0].templateId").value(deleted.getId()))
			.andExpect(jsonPath("$.result.templates[0].deletedAt").value(LATER_TEXT))
			.andExpect(jsonPath("$.result.templates[0].purgeAt").value(PURGE_AT_TEXT));
		mockMvc.perform(get("/api/v1/templates").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.templates[*].templateId").value(contains(kept.getId().intValue())));
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("삭제하면 같은 단축어(대소문자만 달라도)로 새 템플릿을 만들 수 있다")
	void shortcutReleased() throws Exception {
		Template template = saveTemplate(userId, "/Quote");
		clock.setTo(LATER);

		deleteAs(userId, template.getId()).andExpect(status().isOk());

		createAs(userId, "/quote")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("TEMPLATE201_1"));
	}

	@Test
	@DisplayName("한도에 도달해 저장이 막혀 있어도 하나를 삭제하면 다시 저장할 수 있다 (50 → 49 → 50)")
	void limitReleased() throws Exception {
		IntStream.rangeClosed(1, UsageLimits.TEMPLATE_LIMIT - 1)
			.forEach(number -> templateRepository.save(TemplateFixture.template(userId, "/t" + number)));
		Template target = templateRepository.save(TemplateFixture.template(userId, "/대상"));
		flushAndClear();
		createAs(userId, "/새것").andExpect(jsonPath("$.code").value("TEMPLATE403_1"));

		deleteAs(userId, target.getId()).andExpect(status().isOk());

		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(UsageLimits.TEMPLATE_LIMIT - 1);
		createAs(userId, "/새것").andExpect(status().isCreated());
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(UsageLimits.TEMPLATE_LIMIT);
	}

	@Test
	@DisplayName("삭제한 뒤에는 상세(9번)·수정(11번)·사용 기록(14번)이 TEMPLATE404_2")
	void otherApisSeeTrash() throws Exception {
		Template template = saveTemplate(userId, TemplateFixture.SHORTCUT);
		clock.setTo(LATER);
		deleteAs(userId, template.getId()).andExpect(status().isOk());
		flushAndClear();

		expectInTrash(mockMvc.perform(get(PATH, template.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))));
		expectInTrash(mockMvc.perform(put(PATH, template.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\": \"새 이름\", \"shortcut\": \"/견적\", \"content\": \"본문\"}")));
		expectInTrash(mockMvc.perform(post(PATH + "/usages", template.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"channel\": \"DASHBOARD_COPY\"}")));
	}

	@Test
	@DisplayName("없는 템플릿이면 TEMPLATE404_1")
	void notFound() throws Exception {
		expectNotFound(deleteAs(userId, Long.MAX_VALUE));
	}

	@Test
	@DisplayName("다른 회원의 템플릿이면 TEMPLATE404_1이고 휴지통으로 옮기지 않는다")
	void otherUsersTemplate() throws Exception {
		Template template = saveTemplate(otherUserId, TemplateFixture.SHORTCUT);

		expectNotFound(deleteAs(userId, template.getId()));

		assertThat(reload(template).isInTrash()).isFalse();
	}

	@Test
	@DisplayName("내 템플릿이 이미 휴지통에 있으면 TEMPLATE404_2가 아니라 TEMPLATE404_1이고 휴지통 시각·변경 시각이 그대로다")
	void alreadyInTrash() throws Exception {
		Template template = saveTrashed(userId);
		clock.setTo(LATER);

		expectNotFound(deleteAs(userId, template.getId()));

		Template found = reload(template);
		assertThat(found.getDeletedAt()).isEqualTo(FIXED);
		assertThat(found.getUpdatedAt()).isEqualTo(FIXED);
	}

	@Test
	@DisplayName("다른 회원의 휴지통 템플릿이면 TEMPLATE404_1")
	void otherUsersTrashed() throws Exception {
		Template template = saveTrashed(otherUserId);

		expectNotFound(deleteAs(userId, template.getId()));
	}

	@Test
	@DisplayName("templateId가 숫자가 아니면 COMMON400_1")
	void nonNumericTemplateId() throws Exception {
		expectError(mockMvc.perform(delete("/api/v1/templates/abc")
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))), 400, "COMMON400_1", "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1, 캐시 금지")
	void unauthenticated() throws Exception {
		expectError(mockMvc.perform(delete(PATH, 1L)), 401, "COMMON401_1", "로그인이 필요해요.");
	}

	private ResultActions deleteAs(Long userId, Long templateId) throws Exception {
		return mockMvc.perform(delete(PATH, templateId).header(HttpHeaders.AUTHORIZATION, bearer(userId)));
	}

	private ResultActions createAs(Long userId, String shortcut) throws Exception {
		return mockMvc.perform(post("/api/v1/templates")
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\": \"새 견적\", \"shortcut\": \"" + shortcut + "\", \"content\": \"본문\"}"));
	}

	private static void expectNotFound(ResultActions actions) throws Exception {
		expectError(actions, 404, "TEMPLATE404_1", "템플릿을 찾을 수 없어요.");
	}

	private static void expectInTrash(ResultActions actions) throws Exception {
		expectError(actions, 404, "TEMPLATE404_2", "이미 삭제된 템플릿이에요.");
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

	/** 최근 사용 시각이 USED_AT인 템플릿. 수정·생성·변경 시각은 FIXED다 */
	private Template saveUsedTemplate(Long ownerId) {
		Template template = TemplateFixture.template(ownerId);
		template.markUsed(USED_AT);
		templateRepository.saveAndFlush(template);
		flushAndClear();
		return template;
	}

	private Template saveTrashed(Long ownerId) {
		Template template = templateRepository.saveAndFlush(
			TemplateFixture.trashedTemplate(ownerId, TemplateFixture.SHORTCUT, FIXED));
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
