package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** 명세 17·18번. 본인 소유·휴지통 조건과 지운 뒤 목록을 실제 필터·MySQL까지 거쳐 확인한다. */
@Transactional
@Import(MutableClockConfiguration.class)
class TrashPurgeControllerTest extends IntegrationTestSupport {

	private static final String TRASH_PATH = "/api/v1/trash/templates";
	private static final String PURGE_PATH = TRASH_PATH + "/{templateId}";
	private static final String NO_STORE = "no-store";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 25, 10, 0);
	private static final LocalDateTime DELETED_AT = FIXED.minusDays(1);

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
	@DisplayName("영구 삭제하면 200과 지운 ID만 돌려주고, 그 행만 사라진다")
	void purge() throws Exception {
		Long target = saveTrashed(userId, "/견적");
		Long remainingTrashed = saveTrashed(userId, "/회의");
		Long myActive = saveActive(userId, "/보고");

		MvcResult result = purgeAs(userId, target)
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TRASH200_3"))
			.andExpect(jsonPath("$.message").value("템플릿을 영구 삭제했습니다."))
			.andExpect(jsonPath("$.result.templateId").value(target))
			.andReturn();

		assertThat(resultFieldNames(result)).containsExactly("templateId");
		assertThat(templateRepository.existsById(target)).isFalse();
		assertThat(reload(remainingTrashed).isInTrash()).isTrue();
		assertThat(reload(myActive).isInTrash()).isFalse();
	}

	@Test
	@DisplayName("없는 ID, 다른 회원의 활성·휴지통 템플릿, 휴지통에 없는 내 템플릿은 TRASH404_1이고 아무것도 지우지 않는다")
	void purgeNotFound() throws Exception {
		Long othersActive = saveActive(otherUserId, "/견적");
		Long othersTrashed = saveTrashed(otherUserId, "/회의");
		Long myActive = saveActive(userId, "/보고");

		for (Long templateId : new Long[] {Long.MAX_VALUE, othersActive, othersTrashed, myActive}) {
			expectNotFound(purgeAs(userId, templateId));
		}

		assertThat(reload(othersActive).isInTrash()).isFalse();
		assertThat(reload(othersTrashed).isInTrash()).isTrue();
		assertThat(reload(myActive).isInTrash()).isFalse();
	}

	@Test
	@DisplayName("이미 영구 삭제한 템플릿을 다시 지우면 TRASH404_1")
	void purgeTwice() throws Exception {
		Long target = saveTrashed(userId, "/견적");

		purgeAs(userId, target).andExpect(status().isOk());

		expectNotFound(purgeAs(userId, target));
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "99999999999999999999"})
	@DisplayName("templateId가 Long으로 바뀌지 않으면 COMMON400_1")
	void purgeInvalidTemplateId(String templateId) throws Exception {
		expectError(mockMvc.perform(delete(TRASH_PATH + "/" + templateId)
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))), 400, "COMMON400_1", "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("영구 삭제에 토큰이 없으면 COMMON401_1이고 지우지 않는다")
	void purgeUnauthorized() throws Exception {
		Long target = saveTrashed(userId, "/견적");

		expectError(mockMvc.perform(delete(PURGE_PATH, target)), 401, "COMMON401_1", "로그인이 필요해요.");

		assertThat(templateRepository.existsById(target)).isTrue();
	}

	@Test
	@DisplayName("영구 삭제한 템플릿은 휴지통 목록과 개수에서 빠진다")
	void purgeLeavesTrashList() throws Exception {
		Long target = saveTrashed(userId, "/견적");
		Long remaining = saveTrashed(userId, "/회의");

		purgeAs(userId, target).andExpect(status().isOk());

		getTrash(userId)
			.andExpect(jsonPath("$.result.totalCount").value(1))
			.andExpect(jsonPath("$.result.templates[*].templateId", contains(remaining.intValue())));
	}

	@Test
	@DisplayName("영구 삭제한 템플릿의 단축어로 새 템플릿을 만들 수 있다")
	void createWithPurgedShortcut() throws Exception {
		Long target = saveTrashed(userId, "/견적");

		purgeAs(userId, target).andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/templates")
				.header(HttpHeaders.AUTHORIZATION, bearer(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"title": "견적 안내", "shortcut": "/견적", "content": "본문"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.result.shortcut").value("/견적"));
	}

	@Test
	@DisplayName("비우면 200과 지운 개수를 돌려주고, 내 휴지통만 지운다. 활성 템플릿과 다른 회원의 휴지통은 그대로다")
	void empty() throws Exception {
		Long first = saveTrashed(userId, "/견적");
		Long second = saveTrashed(userId, "/견적");
		Long third = saveTrashed(userId, "/회의");
		Long myActive = saveActive(userId, "/견적");
		Long othersTrashed = saveTrashed(otherUserId, "/견적");

		MvcResult result = emptyAs(userId)
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TRASH200_4"))
			.andExpect(jsonPath("$.message").value("휴지통을 비웠습니다."))
			.andExpect(jsonPath("$.result.deletedCount").value(3))
			.andReturn();

		assertThat(resultFieldNames(result)).containsExactly("deletedCount");
		for (Long purged : new Long[] {first, second, third}) {
			assertThat(templateRepository.existsById(purged)).isFalse();
		}
		assertThat(reload(myActive).isInTrash()).isFalse();
		assertThat(reload(othersTrashed).isInTrash()).isTrue();
	}

	@Test
	@DisplayName("휴지통이 비어 있어도 에러가 아니라 deletedCount 0이다")
	void emptyNothing() throws Exception {
		saveActive(userId, "/견적");

		emptyAs(userId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("TRASH200_4"))
			.andExpect(jsonPath("$.result.deletedCount").value(0));

		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("비운 뒤 휴지통 목록은 비어 있다")
	void emptyLeavesTrashList() throws Exception {
		saveTrashed(userId, "/견적");
		saveTrashed(userId, "/회의");

		emptyAs(userId).andExpect(status().isOk());

		getTrash(userId)
			.andExpect(jsonPath("$.result.totalCount").value(0))
			.andExpect(jsonPath("$.result.templates.length()").value(0));
	}

	@Test
	@DisplayName("비우기에 토큰이 없으면 COMMON401_1이고 지우지 않는다")
	void emptyUnauthorized() throws Exception {
		Long trashed = saveTrashed(userId, "/견적");

		expectError(mockMvc.perform(delete(TRASH_PATH)), 401, "COMMON401_1", "로그인이 필요해요.");

		assertThat(templateRepository.existsById(trashed)).isTrue();
	}

	/** 엔티티를 읽으면 복호화에서 500이 나므로, 읽지 않고 지워야 이런 행도 치울 수 있다 */
	@Test
	@DisplayName("본문을 복호화할 수 없는 행도 영구 삭제·비우기로 지운다")
	void undecryptableContent() throws Exception {
		Long brokenOne = saveTrashed(userId, "/견적");
		Long brokenAll = saveTrashed(userId, "/회의");
		breakContent(brokenOne);
		breakContent(brokenAll);

		purgeAs(userId, brokenOne).andExpect(status().isOk());
		emptyAs(userId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.deletedCount").value(1));

		assertThat(templateRepository.existsById(brokenOne)).isFalse();
		assertThat(templateRepository.existsById(brokenAll)).isFalse();
	}

	private ResultActions purgeAs(Long requesterId, Long templateId) throws Exception {
		return mockMvc.perform(delete(PURGE_PATH, templateId).header(HttpHeaders.AUTHORIZATION, bearer(requesterId)));
	}

	private ResultActions emptyAs(Long requesterId) throws Exception {
		return mockMvc.perform(delete(TRASH_PATH).header(HttpHeaders.AUTHORIZATION, bearer(requesterId)));
	}

	private ResultActions getTrash(Long requesterId) throws Exception {
		return mockMvc.perform(get(TRASH_PATH).header(HttpHeaders.AUTHORIZATION, bearer(requesterId)))
			.andExpect(status().isOk());
	}

	private Iterable<String> resultFieldNames(MvcResult result) throws Exception {
		return jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.get("result").propertyNames();
	}

	private static void expectNotFound(ResultActions actions) throws Exception {
		expectError(actions, 404, "TRASH404_1", "휴지통에서 템플릿을 찾을 수 없어요.");
	}

	private static void expectError(ResultActions actions, int status, String code, String message) throws Exception {
		actions.andExpect(status().is(status))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	private void breakContent(Long templateId) {
		entityManager.createNativeQuery("update templates set content = 'not-encrypted' where id = :id")
			.setParameter("id", templateId)
			.executeUpdate();
		entityManager.clear();
	}

	private Long saveActive(Long ownerId, String shortcut) {
		Long id = templateRepository.saveAndFlush(TemplateFixture.template(ownerId, shortcut)).getId();
		entityManager.clear();
		return id;
	}

	private Long saveTrashed(Long ownerId, String shortcut) {
		Long id = templateRepository.saveAndFlush(TemplateFixture.trashedTemplate(ownerId, shortcut, DELETED_AT))
			.getId();
		entityManager.clear();
		return id;
	}

	/** 쌓인 변경을 내보내고 1차 캐시를 비워 DB 값을 다시 읽는다 */
	private Template reload(Long templateId) {
		entityManager.flush();
		entityManager.clear();
		return entityManager.find(Template.class, templateId);
	}

	private Long saveUser(String email) {
		User user = UserFixture.user(email);
		entityManager.persist(user);
		entityManager.flush();
		return user.getId();
	}

}
