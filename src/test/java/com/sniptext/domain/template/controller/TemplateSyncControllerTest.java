package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.sniptext.domain.gallery.repository.GalleryTemplateRepository;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.TemplateCommandService;
import com.sniptext.domain.template.service.dto.command.TemplateCreateFromGalleryCommand;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.GalleryTemplateFixture;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * S2 명세 27번 확장 템플릿 동기화. 각 변경 API를 실제로 부른 뒤 버전이 바뀌는지 MySQL까지 거쳐 확인한다.
 * 클래스에 @Transactional을 붙이지 않는다. 갤러리 저장이 호출자 트랜잭션 밖에서만 돌기 때문이다. 대신 만든 회원을 직접 지운다.
 */
@Import(MutableClockConfiguration.class)
class TemplateSyncControllerTest extends IntegrationTestSupport {

	private static final String SYNC_PATH = "/api/v1/templates/sync";
	private static final String LIST_PATH = "/api/v1/templates";
	private static final String TEMPLATE_PATH = "/api/v1/templates/{templateId}";
	private static final String USAGE_PATH = "/api/v1/templates/{templateId}/usages";
	private static final String RESTORE_PATH = "/api/v1/trash/templates/{templateId}/restore";
	private static final String PURGE_PATH = "/api/v1/trash/templates/{templateId}";
	private static final String EMPTY_TRASH_PATH = "/api/v1/trash/templates";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 11, 15, 20, 31, 10);
	private static final Duration STEP = Duration.ofMinutes(1);

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private GalleryTemplateRepository galleryTemplateRepository;

	@Autowired
	private TemplateCommandService templateCommandService;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JsonMapper jsonMapper;

	private final List<Long> userIds = new ArrayList<>();
	private Long galleryTemplateId;
	private Long userId;
	private Long otherUserId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
		userId = saveUser("sync-owner@sniptext.com");
		otherUserId = saveUser("sync-other@sniptext.com");
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다. 갤러리 원본은 템플릿을 지운 뒤에 지운다
	@AfterEach
	void tearDown() {
		userIds.forEach(userRepository::deleteById);
		userIds.clear();
		if (galleryTemplateId != null) {
			galleryTemplateRepository.deleteById(galleryTemplateId);
		}
	}

	@Test
	@DisplayName("처음 요청(버전 없음)은 changed = true와 8번 목록 전체, 명세 순서의 필드, 캐시 금지 헤더")
	void firstSync() throws Exception {
		Long first = save(userId, "/첫째");
		Long second = save(userId, "/둘째");
		Long third = save(userId, "/셋째");

		JsonNode result = syncResult(get(SYNC_PATH), userId);

		assertThat(result.propertyNames())
			.containsExactly("changed", "version", "syncedAt", "totalCount", "templates");
		assertThat(result.get("changed").asBoolean()).isTrue();
		assertThat(result.get("version").asString()).startsWith("v1.3.");
		assertThat(result.get("syncedAt").asString()).isEqualTo("2026-11-15T20:31:10+09:00");
		assertThat(result.get("totalCount").asInt()).isEqualTo(3);
		assertThat(templateIds(result)).containsExactly(first, second, third);
		assertThat(result.get("templates")).isEqualTo(listResult().get("templates"));
	}

	@Test
	@DisplayName("받은 버전 그대로 다시 요청하면 changed = false, 버전은 같고 totalCount·templates는 null")
	void unchanged() throws Exception {
		save(userId, "/견적");
		String version = version(userId);
		clock.advance(STEP);

		JsonNode result = sync(userId, version);

		assertThat(result.get("changed").asBoolean()).isFalse();
		assertThat(result.get("version").asString()).isEqualTo(version);
		assertThat(result.get("syncedAt").asString()).isEqualTo("2026-11-15T20:32:10+09:00");
		assertThat(result.propertyNames()).contains("totalCount", "templates");
		assertThat(result.get("totalCount").isNull()).isTrue();
		assertThat(result.get("templates").isNull()).isTrue();
	}

	@Test
	@DisplayName("생성(10번) 뒤에는 버전이 바뀌고 새 템플릿이 목록 끝에 들어 있다")
	void changedAfterCreate() throws Exception {
		Long existing = save(userId, "/견적");
		String before = version(userId);
		clock.advance(STEP);

		String body = """
			{"title": "새 템플릿", "shortcut": "/새것", "content": "안녕하세요"}
			""";
		perform(post(LIST_PATH).contentType(MediaType.APPLICATION_JSON).content(body), userId);

		JsonNode result = expectChanged(before);
		assertThat(result.get("totalCount").asInt()).isEqualTo(2);
		assertThat(templateIds(result)).hasSize(2).first().isEqualTo(existing);
		assertThat(result.get("templates").get(1).get("shortcut").asString()).isEqualTo("/새것");
	}

	@Test
	@DisplayName("수정(11번) 뒤에는 개수가 같아도 버전이 바뀌고 고친 내용이 들어 있다")
	void changedAfterUpdate() throws Exception {
		Long templateId = save(userId, "/견적");
		String before = version(userId);
		clock.advance(STEP);

		String body = """
			{"title": "고친 이름", "shortcut": "/견적", "content": "고친 본문 {{이름}}"}
			""";
		perform(put(TEMPLATE_PATH, templateId).contentType(MediaType.APPLICATION_JSON).content(body), userId);

		JsonNode result = expectChanged(before);
		JsonNode template = result.get("templates").get(0);
		assertThat(template.get("title").asString()).isEqualTo("고친 이름");
		assertThat(template.get("content").asString()).isEqualTo("고친 본문 {{이름}}");
		assertThat(template.get("editedAt").asString()).isEqualTo("2026-11-15T20:32:10+09:00");
	}

	@Test
	@DisplayName("휴지통 이동(12번) 뒤에는 버전이 바뀌고 옮긴 템플릿이 빠진다")
	void changedAfterDelete() throws Exception {
		Long kept = save(userId, "/남김");
		Long deleted = save(userId, "/삭제");
		String before = version(userId);
		clock.advance(STEP);

		perform(delete(TEMPLATE_PATH, deleted), userId);

		JsonNode result = expectChanged(before);
		assertThat(result.get("totalCount").asInt()).isEqualTo(1);
		assertThat(templateIds(result)).containsExactly(kept);
	}

	@Test
	@DisplayName("복구(16번) 뒤에는 버전이 바뀌고 되돌린 템플릿이 원래 자리로 돌아온다")
	void changedAfterRestore() throws Exception {
		Long first = save(userId, "/첫째");
		Long trashed = templateRepository.save(TemplateFixture.trashedTemplate(userId, "/가운데", FIXED)).getId();
		Long third = save(userId, "/셋째");
		String before = version(userId);
		clock.advance(STEP);

		perform(post(RESTORE_PATH, trashed), userId);

		JsonNode result = expectChanged(before);
		assertThat(templateIds(result)).containsExactly(first, trashed, third);
	}

	@Test
	@DisplayName("갤러리 저장 뒤에는 버전이 바뀌고 저장한 템플릿이 들어 있다")
	void changedAfterGallerySave() throws Exception {
		galleryTemplateId = galleryTemplateRepository.save(GalleryTemplateFixture.published(1)).getId();
		save(userId, "/주소");
		String before = version(userId);
		clock.advance(STEP);

		templateCommandService.createFromGallery(new TemplateCreateFromGalleryCommand(userId, galleryTemplateId,
			GalleryTemplateFixture.TITLE, GalleryTemplateFixture.SHORTCUT, GalleryTemplateFixture.CONTENT));

		JsonNode result = expectChanged(before);
		assertThat(result.get("totalCount").asInt()).isEqualTo(2);
		assertThat(result.get("templates").get(1).get("shortcut").asString()).isEqualTo(GalleryTemplateFixture.SHORTCUT);
	}

	@Test
	@DisplayName("사용 기록(14번) 뒤에도 버전이 바뀌어 다른 기기의 최근 사용 시각이 맞춰진다")
	void changedAfterUsage() throws Exception {
		Long templateId = save(userId, "/견적");
		String before = version(userId);
		clock.advance(STEP);

		perform(post(USAGE_PATH, templateId).contentType(MediaType.APPLICATION_JSON)
			.content("{\"channel\": \"POPUP_COPY\"}"), userId);

		JsonNode result = expectChanged(before);
		assertThat(result.get("templates").get(0).get("lastUsedAt").asString())
			.isEqualTo("2026-11-15T20:32:10+09:00");
	}

	@Test
	@DisplayName("휴지통 영구 삭제(17번)·비우기(18번)는 목록에 영향이 없다")
	void purgeDoesNotChangeTemplates() throws Exception {
		save(userId, "/남김");
		Long purged = templateRepository.save(TemplateFixture.trashedTemplate(userId, "/영구", FIXED)).getId();
		templateRepository.save(TemplateFixture.trashedTemplate(userId, "/비움", FIXED));
		JsonNode before = syncResult(get(SYNC_PATH), userId).get("templates");
		clock.advance(STEP);

		perform(delete(PURGE_PATH, purged), userId);
		assertThat(syncResult(get(SYNC_PATH), userId).get("templates")).isEqualTo(before);

		perform(delete(EMPTY_TRASH_PATH), userId);
		assertThat(syncResult(get(SYNC_PATH), userId).get("templates")).isEqualTo(before);
	}

	/**
	 * 영구 삭제는 가장 늦은 updated_at을 뒤로 돌릴 수 있다. 시각만으로는 휴지통 이동 전 버전과 같아지므로 개수가 막아야 한다
	 * (TemplateSyncVersion 주석 참고).
	 */
	@Test
	@DisplayName("휴지통 이동 뒤 영구 삭제로 가장 늦은 시각이 이동 전으로 돌아가도 개수가 달라 이동 전 버전과 겹치지 않는다")
	void purgeRollsBackLatestButCountDiffers() throws Exception {
		Long first = save(userId, "/첫째");
		clock.advance(STEP);
		Long second = save(userId, "/둘째");
		String beforeTrash = version(userId);
		clock.advance(STEP);

		perform(delete(TEMPLATE_PATH, first), userId);
		perform(delete(PURGE_PATH, first), userId);

		JsonNode result = expectChanged(beforeTrash);
		String latestPart = beforeTrash.substring(beforeTrash.lastIndexOf('.'));
		assertThat(result.get("version").asString())
			.as("최댓값은 휴지통 이동 전과 같고 개수만 다르다")
			.isEqualTo("v1.1" + latestPart);
		assertThat(templateIds(result)).containsExactly(second);
	}

	@Test
	@DisplayName("다른 회원의 생성·수정·사용 기록은 내 버전에 영향이 없다")
	void othersChangesDoNotAffectMyVersion() throws Exception {
		save(userId, "/견적");
		Long othersTemplate = save(otherUserId, "/남의것");
		String before = version(userId);
		clock.advance(STEP);

		save(otherUserId, "/남의새것");
		perform(post(USAGE_PATH, othersTemplate).contentType(MediaType.APPLICATION_JSON)
			.content("{\"channel\": \"POPUP_COPY\"}"), otherUserId);

		JsonNode result = sync(userId, before);
		assertThat(result.get("changed").asBoolean()).isFalse();
		assertThat(result.get("version").asString()).isEqualTo(before);
	}

	@ParameterizedTest(name = "\"{0}\"")
	@ValueSource(strings = {"", " ", "abc", "v1", "v1.x.y", "v1.1.1731669061000000", "v2.1.0"})
	@DisplayName("버전이 비었거나 형식이 틀리거나 지금 버전이 아니면 에러 없이 changed = true와 전체 목록")
	void invalidVersion(String version) throws Exception {
		Long templateId = save(userId, "/견적");

		JsonNode result = sync(userId, version);

		assertThat(result.get("changed").asBoolean()).isTrue();
		assertThat(result.get("totalCount").asInt()).isEqualTo(1);
		assertThat(templateIds(result)).containsExactly(templateId);
	}

	@Test
	@DisplayName("템플릿이 하나도 없으면(휴지통 포함 0행) 버전은 v1.0.0, totalCount 0과 빈 배열이고 같은 버전이면 changed = false")
	void noTemplates() throws Exception {
		save(otherUserId, "/남의것");

		JsonNode first = syncResult(get(SYNC_PATH), userId);

		assertThat(first.get("changed").asBoolean()).isTrue();
		assertThat(first.get("version").asString()).isEqualTo("v1.0.0");
		assertThat(first.get("totalCount").asInt()).isZero();
		assertThat(first.get("templates").isArray()).isTrue();
		assertThat(first.get("templates").isEmpty()).isTrue();

		assertThat(sync(userId, "v1.0.0").get("changed").asBoolean()).isFalse();
	}

	@Test
	@DisplayName("휴지통만 있으면 개수는 0이지만 시각이 있어 빈 회원과 버전이 다르다")
	void onlyTrash() throws Exception {
		templateRepository.save(TemplateFixture.trashedTemplate(userId, "/삭제", FIXED));

		JsonNode result = syncResult(get(SYNC_PATH), userId);

		assertThat(result.get("version").asString()).startsWith("v1.0.").isNotEqualTo("v1.0.0");
		assertThat(result.get("totalCount").asInt()).isZero();
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1, 캐시 금지")
	void unauthenticated() throws Exception {
		mockMvc.perform(get(SYNC_PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("COMMON401_1"))
			.andExpect(jsonPath("$.message").value("로그인이 필요해요."))
			.andExpect(jsonPath("$.result").doesNotExist());
	}

	/** 예전 버전으로 요청하면 바뀐 것으로 나오고, 새 버전은 예전과 다르다. 그 응답의 result를 돌려준다 */
	private JsonNode expectChanged(String before) throws Exception {
		JsonNode result = sync(userId, before);

		assertThat(result.get("changed").asBoolean()).isTrue();
		assertThat(result.get("version").asString()).isNotEqualTo(before);
		assertThat(result.get("templates").isArray()).isTrue();
		return result;
	}

	private String version(Long ownerId) throws Exception {
		return syncResult(get(SYNC_PATH), ownerId).get("version").asString();
	}

	private JsonNode sync(Long ownerId, String version) throws Exception {
		return syncResult(get(SYNC_PATH).param("version", version), ownerId);
	}

	/** 동기화를 부르고 공통 응답(성공 코드·문구·캐시 금지)을 확인한 뒤 result를 돌려준다 */
	private JsonNode syncResult(MockHttpServletRequestBuilder request, Long ownerId) throws Exception {
		String body = mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TEMPLATE200_7"))
			.andExpect(jsonPath("$.message").value("템플릿 동기화에 성공했습니다."))
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return jsonMapper.readTree(body).get("result");
	}

	private JsonNode listResult() throws Exception {
		String body = mockMvc.perform(get(LIST_PATH).header(HttpHeaders.AUTHORIZATION, bearer(userId)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return jsonMapper.readTree(body).get("result");
	}

	/** 변경 API를 부르고 2xx인지 확인한다 */
	private void perform(MockHttpServletRequestBuilder request, Long ownerId) throws Exception {
		mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
			.andExpect(status().is2xxSuccessful());
	}

	private List<Long> templateIds(JsonNode result) {
		List<Long> ids = new ArrayList<>();
		result.get("templates").forEach(template -> ids.add(template.get("templateId").asLong()));
		return ids;
	}

	private Long save(Long ownerId, String shortcut) {
		Template template = templateRepository.save(TemplateFixture.template(ownerId, shortcut));
		return template.getId();
	}

	private Long saveUser(String email) {
		Long id = userRepository.save(UserFixture.user(email)).getId();
		userIds.add(id);
		return id;
	}

}
