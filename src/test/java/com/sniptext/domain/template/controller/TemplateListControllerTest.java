package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 명세 8번. 범위·정렬·응답 형식을 실제 필터·MySQL까지 거쳐 확인한다. */
@Transactional
@Import(MutableClockConfiguration.class)
class TemplateListControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/templates";
	private static final String RESTORE_PATH = "/api/v1/trash/templates/{templateId}/restore";
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
	@DisplayName("목록을 조회하면 200, 명세 순서의 필드와 +09:00이 붙은 시각, 캐시 금지 헤더")
	void getTemplates() throws Exception {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/Quote"));
		template.edit(TemplateTitle.create("프로젝트 견적 안내"), Shortcut.create("/Quote"), TemplateContent.create(CONTENT),
			FIXED.plusHours(1));
		template.markUsed(FIXED.plusHours(2));
		flushAndClear();

		MvcResult result = getAs(userId)
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TEMPLATE200_1"))
			.andExpect(jsonPath("$.message").value("템플릿 목록 조회에 성공했습니다."))
			.andExpect(jsonPath("$.result.totalCount").value(1))
			.andExpect(jsonPath("$.result.templates[0].templateId").value(template.getId()))
			.andExpect(jsonPath("$.result.templates[0].title").value("프로젝트 견적 안내"))
			.andExpect(jsonPath("$.result.templates[0].shortcut").value("/Quote"))
			.andExpect(jsonPath("$.result.templates[0].lastUsedAt").value("2026-10-07T12:30:00+09:00"))
			.andExpect(jsonPath("$.result.templates[0].createdAt").value("2026-10-07T10:30:00+09:00"))
			.andExpect(jsonPath("$.result.templates[0].editedAt").value("2026-10-07T11:30:00+09:00"))
			.andReturn();

		JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertThat(body.get("result").propertyNames()).containsExactly("totalCount", "templates");
		assertThat(body.get("result").get("templates").get(0).propertyNames())
			.containsExactly("templateId", "title", "shortcut", "content", "variables", "lastUsedAt", "createdAt",
				"editedAt");
	}

	@Test
	@DisplayName("생성 순(템플릿 ID 오름차순)으로 내려주고, 새 템플릿은 맨 뒤에 붙는다")
	void createdOrder() throws Exception {
		Long first = save(userId, "/첫째");
		Long second = save(userId, "/둘째");
		Long third = save(userId, "/셋째");
		flushAndClear();

		expectOrder(first, second, third);

		Long newest = save(userId, "/넷째");
		flushAndClear();

		expectOrder(first, second, third, newest);
	}

	@Test
	@DisplayName("수정·사용 기록으로 시각이 바뀌어도 순서는 그대로다")
	void orderIgnoresEditAndUse() throws Exception {
		Long first = save(userId, "/첫째");
		Long second = save(userId, "/둘째");
		Long third = save(userId, "/셋째");
		// 가운데를 고치고 셋째를 써서, 최근 수정순·오래된 수정순·최근 사용순 어느 쪽이어도 순서가 달라지게 한다
		clock.setTo(FIXED.plusHours(1));
		templateRepository.findById(second).orElseThrow().edit(TemplateTitle.create("고친 이름"),
			Shortcut.create("/둘째"), TemplateContent.create(CONTENT), FIXED.plusHours(1));
		templateRepository.findById(third).orElseThrow().markUsed(FIXED.plusHours(2));
		flushAndClear();

		expectOrder(first, second, third);
	}

	@Test
	@DisplayName("휴지통에서 되돌린 템플릿은 원래 자리로 돌아온다")
	void restoredTemplateBackInPlace() throws Exception {
		Long first = save(userId, "/첫째");
		Template middle = templateRepository.saveAndFlush(TemplateFixture.trashedTemplate(userId, "/가운데", FIXED));
		Long third = save(userId, "/셋째");
		flushAndClear();

		expectOrder(first, third);

		mockMvc.perform(post(RESTORE_PATH, middle.getId()).header(HttpHeaders.AUTHORIZATION, bearer(userId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("TRASH200_2"));
		flushAndClear();

		expectOrder(first, middle.getId(), third);
	}

	@Test
	@DisplayName("휴지통과 다른 회원의 템플릿은 빠진다")
	void excludesTrashAndOthers() throws Exception {
		Long mine = save(userId, "/내것");
		templateRepository.saveAndFlush(TemplateFixture.trashedTemplate(userId, "/삭제", FIXED));
		save(otherUserId, "/남의것");
		flushAndClear();

		getAs(userId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.totalCount").value(1))
			.andExpect(jsonPath("$.result.templates[*].templateId", contains(mine.intValue())));
	}

	@Test
	@DisplayName("템플릿이 없으면 totalCount 0과 빈 배열")
	void noTemplates() throws Exception {
		templateRepository.saveAndFlush(TemplateFixture.trashedTemplate(userId, "/삭제", FIXED));
		save(otherUserId, "/남의것");
		flushAndClear();

		getAs(userId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("TEMPLATE200_1"))
			.andExpect(jsonPath("$.result.totalCount").value(0))
			.andExpect(jsonPath("$.result.templates", empty()));
	}

	@Test
	@DisplayName("본문은 복호화해 내려주고, 변수 목록은 그 본문에서 첫 등장 순으로 중복 없이 뽑는다")
	void contentAndVariables() throws Exception {
		templateRepository.saveAndFlush(TemplateFixture.templateWithContent(userId, CONTENT));
		flushAndClear();

		getAs(userId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.templates[0].content").value(CONTENT))
			.andExpect(jsonPath("$.result.templates[0].variables", contains("금액", "고객명")));
	}

	@Test
	@DisplayName("사용 이력이 없으면 lastUsedAt은 null이다")
	void neverUsed() throws Exception {
		save(userId, "/견적");
		flushAndClear();

		getAs(userId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.templates[0].lastUsedAt").value(nullValue()));
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1, 캐시 금지")
	void unauthenticated() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value("COMMON401_1"))
			.andExpect(jsonPath("$.message").value("로그인이 필요해요."))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	private void expectOrder(Long... templateIds) throws Exception {
		Integer[] expected = new Integer[templateIds.length];
		for (int i = 0; i < templateIds.length; i++) {
			expected[i] = templateIds[i].intValue();
		}
		getAs(userId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.totalCount").value(templateIds.length))
			.andExpect(jsonPath("$.result.templates[*].templateId", contains(expected)));
	}

	private ResultActions getAs(Long userId) throws Exception {
		return mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(userId)));
	}

	private Long save(Long ownerId, String shortcut) {
		return templateRepository.saveAndFlush(TemplateFixture.template(ownerId, shortcut)).getId();
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
