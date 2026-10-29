package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.user.entity.Plan;
import com.sniptext.domain.user.entity.User;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/** 명세 10번. 검증 순서, 한도, 단축어 중복, 응답 형식을 실제 필터·MySQL까지 거쳐 확인한다. */
@Transactional
@Import(MutableClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class TemplateControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/templates";
	private static final String NO_STORE = "no-store";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 7, 10, 30);
	private static final String TITLE = "프로젝트 견적 안내";
	private static final String CONTENT = "안녕하세요 {{고객명}}님,\n문의 주신 프로젝트 견적은 {{금액}}원입니다.\n{{고객명}}님의 일정에 맞춰 진행 가능합니다.";
	private static final List<String> FIELD_ORDER = List.of(
		"templateId", "title", "shortcut", "content", "variables", "lastUsedAt", "createdAt", "editedAt");

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JsonMapper jsonMapper;

	private Long userId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
		userId = saveUser("free@sniptext.com", null);
	}

	@Test
	@DisplayName("저장하면 201, 명세 순서의 필드와 +09:00이 붙은 시각, 캐시 금지 헤더")
	void create() throws Exception {
		MvcResult result = create(userId, TITLE, "/견적", CONTENT)
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TEMPLATE201_1"))
			.andExpect(jsonPath("$.message").value("템플릿이 저장되었습니다."))
			.andExpect(jsonPath("$.result.templateId").isNumber())
			.andExpect(jsonPath("$.result.title").value(TITLE))
			.andExpect(jsonPath("$.result.shortcut").value("/견적"))
			.andExpect(jsonPath("$.result.content").value(CONTENT))
			.andExpect(jsonPath("$.result.variables", contains("고객명", "금액")))
			.andExpect(jsonPath("$.result.lastUsedAt").isEmpty())
			.andExpect(jsonPath("$.result.createdAt").value("2026-10-07T10:30:00+09:00"))
			.andExpect(jsonPath("$.result.editedAt").value("2026-10-07T10:30:00+09:00"))
			.andExpect(jsonPath("$.result.length()").value(FIELD_ORDER.size()))
			.andReturn();

		assertFieldOrder(body(result));
	}

	@Test
	@DisplayName("변수가 없으면 variables는 빈 배열, 형식이 틀린 중괄호는 저장을 막지 않는다")
	void noVariables() throws Exception {
		MvcResult result = create(userId, TITLE, "/견적", "{{고객 명}}님 {{고객명")
			.andExpect(status().isCreated())
			.andReturn();

		assertThat(body(result)).contains("\"variables\":[]");
	}

	@Test
	@DisplayName("DB의 본문은 암호문이다")
	void contentEncrypted() throws Exception {
		create(userId, TITLE, "/견적", CONTENT).andExpect(status().isCreated());

		String stored = (String)entityManager.createNativeQuery("select content from templates where user_id = :userId")
			.setParameter("userId", userId)
			.getSingleResult();

		assertThat(stored).startsWith("v1:").doesNotContain("고객명");
	}

	@Test
	@DisplayName("이름·단축어·본문이 모두 틀리면 이름 에러(TEMPLATE400_1) 하나만 내려준다")
	void allInvalid() throws Exception {
		expectError(create(userId, "", "/", ""), 400, "TEMPLATE400_1", "템플릿 이름을 입력해주세요.");
	}

	@Test
	@DisplayName("단축어가 \"/\"만이고 본문도 비었으면 단축어 에러(TEMPLATE400_3)가 먼저다")
	void shortcutBeforeContent() throws Exception {
		expectError(create(userId, TITLE, "/", ""), 400, "TEMPLATE400_3", "호출 단축어를 입력해주세요.");
	}

	@Test
	@DisplayName("공백만 있는 단축어는 빈 값이 아니라 형식 오류(TEMPLATE400_4)다")
	void blankShortcut() throws Exception {
		expectError(create(userId, TITLE, "   ", CONTENT), 400, "TEMPLATE400_4", "한글·영문·숫자 2~10자로 입력해주세요.");
	}

	@Test
	@DisplayName("필드가 없으면(null) 빈 값 에러다")
	void missingField() throws Exception {
		expectError(postAs(userId, Map.of("title", TITLE, "shortcut", "/견적")), 400, "TEMPLATE400_6",
			"템플릿 내용을 입력해주세요.");
	}

	@Test
	@DisplayName("/Quote가 있으면 /quote는 TEMPLATE409_1이고, 저장을 시도하지 않아 DB 에러 로그(겹친 단축어 포함)가 남지 않는다")
	void duplicateIgnoringCase(CapturedOutput output) throws Exception {
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/Quote"));

		expectError(create(userId, TITLE, "/quote", CONTENT), 409, "TEMPLATE409_1", "이미 사용 중인 단축어예요.");
		assertThat(output.getAll()).doesNotContain("Duplicate entry");
	}

	@Test
	@DisplayName("맥 자모 분리형(NFD) 단축어도 정규화해 같은 단축어로 보고 TEMPLATE409_1")
	void duplicateAfterNormalization() throws Exception {
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/견적"));

		expectError(create(userId, TITLE, Normalizer.normalize("/견적", Normalizer.Form.NFD), CONTENT), 409,
			"TEMPLATE409_1", "이미 사용 중인 단축어예요.");
	}

	@Test
	@DisplayName("휴지통에 있는 템플릿의 단축어로는 새로 저장할 수 있다")
	void trashedShortcutReusable() throws Exception {
		Template trashed = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/견적"));
		trashed.moveToTrash(FIXED);
		templateRepository.flush();

		create(userId, TITLE, "/견적", CONTENT).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("다른 회원이 쓰는 단축어로는 저장할 수 있다")
	void otherUsersShortcut() throws Exception {
		Long otherUserId = saveUser("other@sniptext.com", null);
		templateRepository.saveAndFlush(TemplateFixture.template(otherUserId, "/견적"));

		create(userId, TITLE, "/견적", CONTENT).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("FREE는 4개에서 5번째를 저장하고, 6번째는 TEMPLATE403_1")
	void freeLimit() throws Exception {
		saveTemplates(userId, 4);

		create(userId, TITLE, "/다섯째", CONTENT).andExpect(status().isCreated());
		expectError(create(userId, TITLE, "/여섯째", CONTENT), 403, "TEMPLATE403_1", "템플릿 저장 한도에 도달했어요.");
	}

	@Test
	@DisplayName("휴지통 템플릿은 한도 개수에 들어가지 않는다")
	void trashNotCounted() throws Exception {
		saveTemplates(userId, 5);
		templateRepository.findAll().stream()
			.filter(template -> template.getUserId().equals(userId))
			.findFirst()
			.orElseThrow()
			.moveToTrash(FIXED);
		templateRepository.flush();

		create(userId, TITLE, "/다섯째", CONTENT).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("PRO(plan_override)는 49개에서 50번째를 저장하고, 51번째는 TEMPLATE403_1")
	void proLimit() throws Exception {
		Long proUserId = saveUser("pro@sniptext.com", Plan.PRO);
		saveTemplates(proUserId, 49);

		create(proUserId, TITLE, "/쉰째", CONTENT).andExpect(status().isCreated());
		expectError(create(proUserId, TITLE, "/쉰한째", CONTENT), 403, "TEMPLATE403_1", "템플릿 저장 한도에 도달했어요.");
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1, 캐시 금지")
	void unauthenticated() throws Exception {
		expectError(mockMvc.perform(post(PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(Map.of("title", TITLE, "shortcut", "/견적", "content", CONTENT)))), 401, "COMMON401_1",
			"로그인이 필요해요.");
	}

	private ResultActions create(Long userId, String title, String shortcut, String content) throws Exception {
		return postAs(userId, Map.of("title", title, "shortcut", shortcut, "content", content));
	}

	private ResultActions postAs(Long userId, Map<String, String> body) throws Exception {
		return mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearer(userId))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(body)));
	}

	private static void expectError(ResultActions actions, int status, String code, String message) throws Exception {
		actions.andExpect(status().is(status))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	private String json(Map<String, String> body) {
		return jsonMapper.writeValueAsString(body);
	}

	/** 단축어 /t1, /t2 ... 로 활성 템플릿을 미리 채운다 */
	private void saveTemplates(Long ownerId, int count) {
		for (int i = 1; i <= count; i++) {
			templateRepository.save(TemplateFixture.template(ownerId, "/t" + i));
		}
		templateRepository.flush();
	}

	// TODO : 회원 픽스처는 가입 이슈에서 User.create와 함께 만든다. 그 전까지 BaseTimeEntityTest처럼 직접 저장한다
	private Long saveUser(String email, Plan planOverride) {
		User user = User.builder()
			.email(email)
			.nickname("닉네임")
			.planOverride(planOverride)
			.build();
		entityManager.persist(user);
		entityManager.flush();
		return user.getId();
	}

	private static String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

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
