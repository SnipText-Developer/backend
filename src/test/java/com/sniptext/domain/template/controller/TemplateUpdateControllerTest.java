package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

/** 명세 11번. 수정 규칙과 시각 컬럼을 실제 필터·MySQL까지 거쳐 확인한다. */
@Transactional
@Import(MutableClockConfiguration.class)
class TemplateUpdateControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/templates/{templateId}";
	private static final String NO_STORE = "no-store";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 7, 10, 30);
	private static final LocalDateTime LATER = FIXED.plusHours(1);
	private static final String LATER_TEXT = "2026-10-07T11:30:00+09:00";
	private static final String FIXED_TEXT = "2026-10-07T10:30:00+09:00";

	private static final String TITLE = "프로젝트 견적 안내 (수정)";
	private static final String SHORTCUT = "/새견적";
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
	@DisplayName("수정하면 200과 명세 순서의 필드를 돌려주고 DB에 저장한다. edited_at·updated_at은 지금 시각, last_used_at은 그대로다")
	void update() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		template.markUsed(FIXED.plusMinutes(5));
		flushAndClear();
		clock.setTo(LATER);

		MvcResult result = putAs(userId, template.getId(), TITLE, SHORTCUT, CONTENT)
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TEMPLATE200_3"))
			.andExpect(jsonPath("$.message").value("수정 내용이 저장되었습니다."))
			.andExpect(jsonPath("$.result.templateId").value(template.getId()))
			.andExpect(jsonPath("$.result.title").value(TITLE))
			.andExpect(jsonPath("$.result.shortcut").value(SHORTCUT))
			.andExpect(jsonPath("$.result.content").value(CONTENT))
			.andExpect(jsonPath("$.result.variables", contains("금액", "고객명")))
			.andExpect(jsonPath("$.result.lastUsedAt").value("2026-10-07T10:35:00+09:00"))
			.andExpect(jsonPath("$.result.createdAt").value(FIXED_TEXT))
			.andExpect(jsonPath("$.result.editedAt").value(LATER_TEXT))
			.andReturn();

		assertThat(jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.get("result").propertyNames())
			.containsExactly("templateId", "title", "shortcut", "content", "variables", "lastUsedAt", "createdAt",
				"editedAt");

		Template found = reload(template);
		assertThat(found.getTitle().getValue()).isEqualTo(TITLE);
		assertThat(found.getShortcut().getValue()).isEqualTo(SHORTCUT);
		assertThat(found.getContent().getValue()).isEqualTo(CONTENT);
		assertThat(found.getEditedAt()).isEqualTo(LATER);
		assertThat(found.getUpdatedAt()).isEqualTo(LATER);
		assertThat(found.getLastUsedAt()).isEqualTo(FIXED.plusMinutes(5));
		assertThat(found.getCreatedAt()).isEqualTo(FIXED);
	}

	@Test
	@DisplayName("정리한 뒤 세 값이 기존과 같으면 UPDATE가 나가지 않아 edited_at·updated_at·암호문이 그대로다")
	void sameValuesChangeNothing() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		String storedBefore = storedContent(template.getId());
		entityManager.clear();
		clock.setTo(LATER);

		putAs(userId, template.getId(), "  " + TemplateFixture.TITLE + "  ",
			Normalizer.normalize(TemplateFixture.SHORTCUT, Normalizer.Form.NFD),
			TemplateFixture.CONTENT.replace("\n", "\r\n"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("TEMPLATE200_3"))
			.andExpect(jsonPath("$.result.editedAt").value(FIXED_TEXT));

		Template found = reload(template);
		assertThat(found.getEditedAt()).isEqualTo(FIXED);
		assertThat(found.getUpdatedAt()).isEqualTo(FIXED);
		assertThat(storedContent(template.getId())).isEqualTo(storedBefore);
	}

	@Test
	@DisplayName("단축어 대소문자만 바꿔도 수정이고, 비교 키는 그대로다")
	void caseOnlyShortcutChange() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/Quote"));
		flushAndClear();
		clock.setTo(LATER);

		putAs(userId, template.getId(), TemplateFixture.TITLE, "/quote", TemplateFixture.CONTENT)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.shortcut").value("/quote"))
			.andExpect(jsonPath("$.result.editedAt").value(LATER_TEXT));

		Template found = reload(template);
		assertThat(found.getShortcut().getValue()).isEqualTo("/quote");
		assertThat(found.getShortcut().getKey()).isEqualTo("/quote");
		assertThat(found.getEditedAt()).isEqualTo(LATER);
	}

	@Test
	@DisplayName("자기 자신의 단축어를 그대로 두고 이름만 바꾸면 중복이 아니다")
	void ownShortcutIsNotDuplicate() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		flushAndClear();

		putAs(userId, template.getId(), TITLE, TemplateFixture.SHORTCUT, TemplateFixture.CONTENT)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.title").value(TITLE));
	}

	@ParameterizedTest
	@MethodSource("duplicateShortcuts")
	@DisplayName("내 다른 활성 템플릿과 비교 키가 같으면 TEMPLATE409_1이고 아무것도 바뀌지 않는다 (자모 분리형·대소문자 포함)")
	void duplicateOfOtherActiveTemplate(String shortcut) throws Exception {
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/견적"));
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/Quote"));
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/내단축어"));
		flushAndClear();

		expectError(putAs(userId, template.getId(), TITLE, shortcut, CONTENT), 409, "TEMPLATE409_1", "이미 사용 중인 단축어예요.");

		Template found = reload(template);
		assertThat(found.getTitle().getValue()).isEqualTo(TemplateFixture.TITLE);
		assertThat(found.getShortcut().getValue()).isEqualTo("/내단축어");
	}

	private static Stream<String> duplicateShortcuts() {
		return Stream.of("/견적", Normalizer.normalize("/견적", Normalizer.Form.NFD), "/QUOTE", "/quote");
	}

	@Test
	@DisplayName("다른 회원이 쓰는 단축어는 중복이 아니다")
	void otherUsersShortcutIsNotDuplicate() throws Exception {
		templateRepository.saveAndFlush(TemplateFixture.template(otherUserId, SHORTCUT));
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		flushAndClear();

		putAs(userId, template.getId(), TITLE, SHORTCUT, CONTENT)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.shortcut").value(SHORTCUT));
	}

	@Test
	@DisplayName("휴지통 템플릿이 쓰던 단축어로 바꿀 수 있다")
	void shortcutOfTrashedTemplateIsAvailable() throws Exception {
		Template trashed = templateRepository.saveAndFlush(TemplateFixture.template(userId, SHORTCUT));
		trashed.moveToTrash(FIXED);
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		flushAndClear();

		putAs(userId, template.getId(), TITLE, SHORTCUT, CONTENT)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.shortcut").value(SHORTCUT));
	}

	@Test
	@DisplayName("수정한 본문도 DB에는 암호문으로 저장한다")
	void editedContentStaysEncrypted() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		flushAndClear();

		putAs(userId, template.getId(), TITLE, SHORTCUT, CONTENT).andExpect(status().isOk());
		flushAndClear();

		assertThat(storedContent(template.getId())).startsWith("v1:").doesNotContain("고객명");
	}

	@Test
	@DisplayName("없는 템플릿이면 TEMPLATE404_1")
	void notFound() throws Exception {
		expectNotFound(putAs(userId, Long.MAX_VALUE, TITLE, SHORTCUT, CONTENT));
	}

	@Test
	@DisplayName("다른 회원의 템플릿이면 TEMPLATE404_1이고 바뀌지 않는다")
	void otherUsersTemplate() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(otherUserId));
		flushAndClear();

		expectNotFound(putAs(userId, template.getId(), TITLE, SHORTCUT, CONTENT));

		assertThat(reload(template).getTitle().getValue()).isEqualTo(TemplateFixture.TITLE);
	}

	@Test
	@DisplayName("내 템플릿이 휴지통에 있으면 TEMPLATE404_2")
	void trashed() throws Exception {
		Template template = saveTrashed(userId);

		expectError(putAs(userId, template.getId(), TITLE, SHORTCUT, CONTENT), 404, "TEMPLATE404_2", "이미 삭제된 템플릿이에요.");
	}

	@Test
	@DisplayName("다른 회원의 휴지통 템플릿이면 TEMPLATE404_1이다 (존재 여부를 노출하지 않는다)")
	void otherUsersTrashed() throws Exception {
		Template template = saveTrashed(otherUserId);

		expectNotFound(putAs(userId, template.getId(), TITLE, SHORTCUT, CONTENT));
	}

	@ParameterizedTest
	@MethodSource("invalidBodies")
	@DisplayName("형식이 틀리면 명세의 에러를 돌려주고, 여러 개가 틀리면 이름 → 단축어 → 내용 순서로 첫 에러만 내려준다")
	void invalidBody(String title, String shortcut, String content, String code, String message) throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		flushAndClear();

		expectError(putAs(userId, template.getId(), title, shortcut, content), 400, code, message);

		assertThat(reload(template).getTitle().getValue()).isEqualTo(TemplateFixture.TITLE);
	}

	private static Stream<Arguments> invalidBodies() {
		return Stream.of(
			arguments("   ", "/", "   ", "TEMPLATE400_1", "템플릿 이름을 입력해주세요."),
			arguments("가".repeat(51), SHORTCUT, CONTENT, "TEMPLATE400_2", "템플릿 이름은 50자까지 입력할 수 있어요."),
			arguments(TITLE, "/", "   ", "TEMPLATE400_3", "호출 단축어를 입력해주세요."),
			arguments(TITLE, "/가", CONTENT, "TEMPLATE400_4", "한글·영문·숫자 2~10자로 입력해주세요."),
			arguments(TITLE, "/톤교정", CONTENT, "TEMPLATE400_5", "사용할 수 없는 단축어예요."),
			arguments(TITLE, SHORTCUT, "   ", "TEMPLATE400_6", "템플릿 내용을 입력해주세요."),
			arguments(TITLE, SHORTCUT, "가".repeat(3001), "TEMPLATE400_7", "템플릿 내용은 3,000자까지 입력할 수 있어요.")
		);
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1, 캐시 금지")
	void unauthenticated() throws Exception {
		expectError(mockMvc.perform(put(PATH, 1L)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(TITLE, SHORTCUT, CONTENT))), 401, "COMMON401_1", "로그인이 필요해요.");
	}

	private ResultActions putAs(Long userId, Long templateId, String title, String shortcut, String content)
		throws Exception {
		return mockMvc.perform(put(PATH, templateId)
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(title, shortcut, content)));
	}

	private String body(String title, String shortcut, String content) {
		return jsonMapper.writeValueAsString(Map.of("title", title, "shortcut", shortcut, "content", content));
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

	private String storedContent(Long templateId) {
		return (String)entityManager.createNativeQuery("select content from templates where id = :id")
			.setParameter("id", templateId)
			.getSingleResult();
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
