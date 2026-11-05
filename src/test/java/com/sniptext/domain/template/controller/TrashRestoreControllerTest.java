package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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

/**
 * 명세 16번. 한도·자동 번호·시각 컬럼을 실제 필터·MySQL까지 거쳐 확인한다.
 * 번호 소진(TEMPLATE409_2)과 저장 시 유니크 위반은 실제 행으로 닿을 수 없어 TrashRestoreConflictTest에서 본다.
 */
@Transactional
@Import(MutableClockConfiguration.class)
class TrashRestoreControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/trash/templates/{templateId}/restore";
	private static final String NO_STORE = "no-store";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 7, 10, 30);
	private static final LocalDateTime USED_AT = FIXED.plusMinutes(5);
	private static final LocalDateTime DELETED_AT = FIXED.plusMinutes(10);
	private static final LocalDateTime LATER = FIXED.plusHours(1);

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
	@DisplayName("복구하면 200과 명세 순서의 필드를 돌려주고, deleted_at만 비우고 updated_at은 지금 시각, 나머지 시각은 그대로다")
	void restore() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/회의"));
		template.markUsed(USED_AT);
		template.moveToTrash(DELETED_AT);
		flushAndClear();
		clock.setTo(LATER);

		MvcResult result = restoreAs(userId, template.getId())
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TRASH200_2"))
			.andExpect(jsonPath("$.message").value("템플릿을 복구했습니다."))
			.andExpect(jsonPath("$.result.templateId").value(template.getId()))
			.andExpect(jsonPath("$.result.title").value(TemplateFixture.TITLE))
			.andExpect(jsonPath("$.result.shortcut").value("/회의"))
			.andExpect(jsonPath("$.result.originalShortcut").value("/회의"))
			.andExpect(jsonPath("$.result.shortcutChanged").value(false))
			.andReturn();

		assertThat(jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.get("result").propertyNames())
			.containsExactly("templateId", "title", "shortcut", "originalShortcut", "shortcutChanged");

		Template found = reload(template.getId());
		assertThat(found.getDeletedAt()).isNull();
		assertThat(found.getActiveShortcutKey()).isEqualTo("/회의");
		assertThat(found.getUpdatedAt()).isEqualTo(LATER);
		assertThat(found.getEditedAt()).isEqualTo(FIXED);
		assertThat(found.getLastUsedAt()).isEqualTo(USED_AT);
		assertThat(found.getCreatedAt()).isEqualTo(FIXED);
		assertThat(found.getContent().getValue()).isEqualTo(TemplateFixture.CONTENT);
	}

	@Test
	@DisplayName("활성 템플릿과 단축어가 겹치면 /견적 → /견적2로 복구하고 바뀌었다고 알려 준다")
	void numbered() throws Exception {
		saveActive(userId, "/견적");
		Long trashedId = saveTrashed(userId, "/견적");

		expectRestored(restoreAs(userId, trashedId), "/견적2", "/견적", true);

		Template found = reload(trashedId);
		assertThat(found.getShortcut().getValue()).isEqualTo("/견적2");
		assertThat(found.getShortcut().getKey()).isEqualTo("/견적2");
		assertThat(found.getActiveShortcutKey()).isEqualTo("/견적2");
	}

	@Test
	@DisplayName("대소문자만 달라도 겹치므로 번호를 붙이고, 삭제 당시의 대소문자는 그대로 둔다")
	void caseInsensitiveConflict() throws Exception {
		saveActive(userId, "/Quote");
		Long trashedId = saveTrashed(userId, "/quote");

		expectRestored(restoreAs(userId, trashedId), "/quote2", "/quote", true);
	}

	@Test
	@DisplayName("자모 분리형으로 입력한 활성 단축어도 같은 글자라 겹친다")
	void decomposedConflict() throws Exception {
		saveActive(userId, Normalizer.normalize("/견적", Normalizer.Form.NFD));
		Long trashedId = saveTrashed(userId, "/견적");

		expectRestored(restoreAs(userId, trashedId), "/견적2", "/견적", true);
	}

	@Test
	@DisplayName("/ 뒤가 10자를 넘으면 원본을 뒤에서 잘라 맞춘다")
	void truncated() throws Exception {
		saveActive(userId, "/견적요청서작성안내문");
		Long trashedId = saveTrashed(userId, "/견적요청서작성안내문");

		expectRestored(restoreAs(userId, trashedId), "/견적요청서작성안내2", "/견적요청서작성안내문", true);
	}

	@Test
	@DisplayName("휴지통에 남은 같은 단축어는 충돌이 아니라 원본 그대로 복구한다")
	void trashedShortcutIsNotConflict() throws Exception {
		saveTrashed(userId, "/견적");
		Long trashedId = saveTrashed(userId, "/견적");

		expectRestored(restoreAs(userId, trashedId), "/견적", "/견적", false);
	}

	@Test
	@DisplayName("활성 49개에서 복구하면 50개가 된다")
	void belowLimit() throws Exception {
		saveActiveCount(49);
		Long trashedId = saveTrashed(userId, "/견적");

		restoreAs(userId, trashedId).andExpect(status().isOk());

		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(50);
	}

	@Test
	@DisplayName("활성 50개면 TEMPLATE403_1이고 휴지통에 그대로 있다")
	void limitReached() throws Exception {
		saveActiveCount(50);
		Long trashedId = saveTrashed(userId, "/견적");

		expectError(restoreAs(userId, trashedId), 403, "TEMPLATE403_1", "템플릿 저장 한도에 도달했어요.");

		assertThat(reload(trashedId).isInTrash()).isTrue();
	}

	/** 원본과 2~99를 모두 채우면 활성 99개라 한도도 이미 넘는다. 실제 행 그대로 한도가 먼저 걸린다 */
	@Test
	@DisplayName("한도와 자동 번호 소진이 겹치면 한도가 먼저다")
	void limitBeforeExhausted() throws Exception {
		saveActive(userId, "/견적");
		IntStream.rangeClosed(2, 99).forEach(number -> saveActive(userId, "/견적" + number));
		Long trashedId = saveTrashed(userId, "/견적");

		expectError(restoreAs(userId, trashedId), 403, "TEMPLATE403_1", "템플릿 저장 한도에 도달했어요.");
	}

	@Test
	@DisplayName("없는 ID, 다른 회원의 활성·휴지통 템플릿, 휴지통에 없는 내 템플릿은 TRASH404_1이고 아무것도 바뀌지 않는다")
	void notFound() throws Exception {
		Long othersActive = saveActive(otherUserId, "/견적");
		Long othersTrashed = saveTrashed(otherUserId, "/회의");
		Long myActive = saveActive(userId, "/보고");

		for (Long templateId : new Long[] {Long.MAX_VALUE, othersActive, othersTrashed, myActive}) {
			expectError(restoreAs(userId, templateId), 404, "TRASH404_1", "휴지통에서 템플릿을 찾을 수 없어요.");
		}

		assertThat(reload(othersTrashed).isInTrash()).isTrue();
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1")
	void unauthorized() throws Exception {
		Long trashedId = saveTrashed(userId, "/견적");

		expectError(mockMvc.perform(post(PATH, trashedId)), 401, "COMMON401_1", "로그인이 필요해요.");
	}

	@Test
	@DisplayName("복구한 템플릿은 휴지통 목록과 개수에서 빠지고 활성 개수에 들어간다")
	void leavesTrashList() throws Exception {
		Long restoredId = saveTrashed(userId, "/견적");
		Long remainingId = saveTrashed(userId, "/회의");

		restoreAs(userId, restoredId).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/trash/templates").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.totalCount").value(1))
			.andExpect(jsonPath("$.result.templates.length()").value(1))
			.andExpect(jsonPath("$.result.templates[0].templateId").value(remainingId));
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(1);
	}

	private ResultActions restoreAs(Long userId, Long templateId) throws Exception {
		return mockMvc.perform(post(PATH, templateId).header(HttpHeaders.AUTHORIZATION, bearer(userId)));
	}

	private static void expectRestored(ResultActions actions, String shortcut, String originalShortcut,
		boolean shortcutChanged) throws Exception {
		actions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("TRASH200_2"))
			.andExpect(jsonPath("$.result.shortcut").value(shortcut))
			.andExpect(jsonPath("$.result.originalShortcut").value(originalShortcut))
			.andExpect(jsonPath("$.result.shortcutChanged").value(shortcutChanged));
	}

	private static void expectError(ResultActions actions, int status, String code, String message) throws Exception {
		actions.andExpect(status().is(status))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	private Long saveActive(Long ownerId, String shortcut) {
		Long id = templateRepository.saveAndFlush(TemplateFixture.template(ownerId, shortcut)).getId();
		entityManager.clear();
		return id;
	}

	/** 단축어 /t1, /t2 ... 로 활성 템플릿을 채운다 */
	private void saveActiveCount(int count) {
		IntStream.rangeClosed(1, count).forEach(number -> saveActive(userId, "/t" + number));
	}

	private Long saveTrashed(Long ownerId, String shortcut) {
		Long id = templateRepository.saveAndFlush(TemplateFixture.trashedTemplate(ownerId, shortcut, DELETED_AT))
			.getId();
		entityManager.clear();
		return id;
	}

	/** 쌓인 변경을 내보내고 1차 캐시를 비워 DB 값을 다시 읽는다 */
	private Template reload(Long templateId) {
		flushAndClear();
		return entityManager.find(Template.class, templateId);
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
