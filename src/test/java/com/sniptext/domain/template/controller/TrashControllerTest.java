package com.sniptext.domain.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.user.entity.User;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.persistence.EntityManager;

/** 명세 15번. 정렬, 커서 페이지 나눔, 응답 형식을 실제 필터·MySQL까지 거쳐 확인한다. */
@Transactional
@Import(MutableClockConfiguration.class)
class TrashControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/trash/templates";
	private static final String NO_STORE = "no-store";
	private static final String INVALID_PAGE_CODE = "TRASH400_1";
	private static final String INVALID_PAGE_MESSAGE = "잘못된 페이지 요청이에요.";
	private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 10, 7, 10, 58, 0, 123_456_000);
	private static final List<String> ITEM_FIELD_ORDER = List.of(
		"templateId", "title", "shortcut", "deletedAt", "purgeAt");
	private static final List<String> RESULT_FIELD_ORDER = List.of(
		"totalCount", "templates", "hasNext", "nextCursor");

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private MutableClock clock;

	private Long userId;
	private Long otherUserId;

	@BeforeEach
	void setUp() {
		clock.setTo(LocalDateTime.of(2026, 10, 25, 10, 0));
		userId = saveUser("owner@sniptext.com");
		otherUserId = saveUser("other@sniptext.com");
	}

	@Test
	@DisplayName("조회하면 200, 명세 순서의 필드와 +09:00이 붙은 시각, 캐시 금지 헤더")
	void list() throws Exception {
		Long templateId = saveTrashed(userId, "/회의", DELETED_AT);

		MvcResult result = list(userId)
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("TRASH200_1"))
			.andExpect(jsonPath("$.message").value("휴지통 조회에 성공했습니다."))
			.andExpect(jsonPath("$.result.length()").value(RESULT_FIELD_ORDER.size()))
			.andExpect(jsonPath("$.result.totalCount").value(1))
			.andExpect(jsonPath("$.result.hasNext").value(false))
			.andExpect(jsonPath("$.result.nextCursor").isEmpty())
			.andExpect(jsonPath("$.result.templates.length()").value(1))
			.andExpect(jsonPath("$.result.templates[0].length()").value(ITEM_FIELD_ORDER.size()))
			.andExpect(jsonPath("$.result.templates[0].templateId").value(templateId))
			.andExpect(jsonPath("$.result.templates[0].title").value(TemplateFixture.TITLE))
			.andExpect(jsonPath("$.result.templates[0].shortcut").value("/회의"))
			.andExpect(jsonPath("$.result.templates[0].deletedAt").value("2026-10-07T10:58:00+09:00"))
			.andExpect(jsonPath("$.result.templates[0].purgeAt").value("2026-11-06T10:58:00+09:00"))
			.andReturn();

		assertFieldOrder(body(result), RESULT_FIELD_ORDER);
		assertFieldOrder(body(result), ITEM_FIELD_ORDER);
	}

	@Test
	@DisplayName("최근 삭제순이고, 같은 시각이면 ID가 큰 것이 먼저다")
	void order() throws Exception {
		Long older = saveTrashed(userId, "/오래", DELETED_AT.minusDays(1));
		Long tiedFirst = saveTrashed(userId, "/동률1", DELETED_AT);
		Long tiedSecond = saveTrashed(userId, "/동률2", DELETED_AT);
		Long newer = saveTrashed(userId, "/최근", DELETED_AT.plusDays(1));

		assertThat(templateIds(body(list(userId).andExpect(status().isOk()).andReturn())))
			.containsExactly(newer, tiedSecond, tiedFirst, older);
	}

	@Test
	@DisplayName("전체 개수는 이번 페이지 개수가 아니라 본인 휴지통 전체다")
	void totalCountIsNotPageSize() throws Exception {
		saveTrashed(userId, "/가나", DELETED_AT);
		saveTrashed(userId, "/다라", DELETED_AT);
		saveTrashed(userId, "/마바", DELETED_AT);

		list(userId, "size", "1")
			.andExpect(jsonPath("$.result.totalCount").value(3))
			.andExpect(jsonPath("$.result.templates.length()").value(1));
	}

	@Test
	@DisplayName("예정 시각이 지난 항목도 배치가 지우기 전까지 그대로 보인다")
	void purgeAtPassed() throws Exception {
		saveTrashed(userId, "/지남", LocalDateTime.now(clock).minusDays(31));

		list(userId)
			.andExpect(jsonPath("$.result.totalCount").value(1))
			.andExpect(jsonPath("$.result.templates[0].purgeAt").value("2026-10-24T10:00:00+09:00"));
	}

	@Test
	@DisplayName("휴지통이 비어 있으면 빈 목록, 전체 개수 0, 다음 페이지 없음")
	void emptyTrash() throws Exception {
		list(userId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.totalCount").value(0))
			.andExpect(jsonPath("$.result.templates", empty()))
			.andExpect(jsonPath("$.result.hasNext").value(false))
			.andExpect(jsonPath("$.result.nextCursor").isEmpty());
	}

	@Test
	@DisplayName("활성 템플릿과 다른 회원의 휴지통은 보이지 않고 개수에도 안 들어간다")
	void excludesActiveAndOthers() throws Exception {
		Long mine = saveTrashed(userId, "/내것", DELETED_AT);
		saveTrashed(otherUserId, "/남의것", DELETED_AT);
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/활성"));

		list(userId)
			.andExpect(jsonPath("$.result.totalCount").value(1))
			.andExpect(jsonPath("$.result.templates[*].templateId", contains(mine.intValue())));
	}

	@Test
	@DisplayName("size를 안 보내면 20개씩 준다")
	void defaultSize() throws Exception {
		saveTrashedCount(21);

		list(userId)
			.andExpect(jsonPath("$.result.templates.length()").value(20))
			.andExpect(jsonPath("$.result.hasNext").value(true));
	}

	@Test
	@DisplayName("size 1을 받아 1개를 준다")
	void minSize() throws Exception {
		saveTrashedCount(2);

		list(userId, "size", "1")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.templates.length()").value(1))
			.andExpect(jsonPath("$.result.hasNext").value(true));
	}

	@Test
	@DisplayName("size 50을 받아 50개를 준다")
	void maxSize() throws Exception {
		saveTrashedCount(51);

		list(userId, "size", "50")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.templates.length()").value(50))
			.andExpect(jsonPath("$.result.hasNext").value(true));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "51", "-1"})
	@DisplayName("size가 1~50 밖이면 TRASH400_1")
	void sizeOutOfRange(String size) throws Exception {
		expectError(list(userId, "size", size), 400, INVALID_PAGE_CODE, INVALID_PAGE_MESSAGE);
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "1.5"})
	@DisplayName("숫자가 아닌 size는 전역 처리의 COMMON400_1")
	void sizeNotNumber(String size) throws Exception {
		expectError(list(userId, "size", size), 400, "COMMON400_1", "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("int 범위를 넘는 size는 숫자로 바꾸지 못해 COMMON400_1")
	void sizeOverflow() throws Exception {
		expectError(list(userId, "size", "99999999999"), 400, "COMMON400_1", "잘못된 요청입니다.");
	}

	@Test
	@DisplayName("비어 있는 size는 기본값, 비어 있는 cursor는 첫 요청이다")
	void emptyParams() throws Exception {
		saveTrashedCount(21);

		list(userId, "size", "", "cursor", "")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.templates.length()").value(20));
	}

	@Test
	@DisplayName("남은 개수와 size가 같으면 마지막 페이지다")
	void lastPageExactSize() throws Exception {
		saveTrashedCount(2);

		list(userId, "size", "2")
			.andExpect(jsonPath("$.result.templates.length()").value(2))
			.andExpect(jsonPath("$.result.hasNext").value(false))
			.andExpect(jsonPath("$.result.nextCursor").isEmpty());
	}

	@Test
	@DisplayName("페이지를 끝까지 넘기면 누락·중복 없이 처음 순서대로 모두 나온다")
	void walkAllPages() throws Exception {
		saveTrashed(userId, "/가나", DELETED_AT.minusDays(1));
		saveTrashed(userId, "/다라", DELETED_AT);
		saveTrashed(userId, "/마바", DELETED_AT);
		saveTrashed(userId, "/사아", DELETED_AT);
		saveTrashed(userId, "/자차", DELETED_AT.plusDays(1));
		List<Long> expected = templateIds(body(list(userId).andReturn()));

		assertThat(expected).hasSize(5);
		assertThat(collectPages(2)).containsExactlyElementsOf(expected);
	}

	@Test
	@DisplayName("같은 초 안에서 마이크로초만 다른 삭제 시각이 페이지 경계에 걸려도 건너뛰지 않는다")
	void walkPagesWithinSameSecond() throws Exception {
		Long earlier = saveTrashed(userId, "/앞쪽", DELETED_AT);
		Long later = saveTrashed(userId, "/뒤쪽", DELETED_AT.plusNanos(1_000));

		assertThat(collectPages(1)).containsExactly(later, earlier);
	}

	@Test
	@DisplayName("페이지 사이에 복구·영구 삭제·새 삭제가 일어나도 남은 항목이 누락·중복되지 않는다")
	void changesBetweenPages() throws Exception {
		Long oldest = saveTrashed(userId, "/가나", DELETED_AT.minusDays(4));
		Long purged = saveTrashed(userId, "/다라", DELETED_AT.minusDays(3));
		Long restored = saveTrashed(userId, "/마바", DELETED_AT.minusDays(2));
		Long secondOnFirstPage = saveTrashed(userId, "/사아", DELETED_AT.minusDays(1));
		Long firstOnFirstPage = saveTrashed(userId, "/자차", DELETED_AT);

		MvcResult firstPage = list(userId, "size", "2").andReturn();
		assertThat(templateIds(body(firstPage))).containsExactly(firstOnFirstPage, secondOnFirstPage);

		restore(restored);
		restore(secondOnFirstPage);
		templateRepository.deleteById(purged);
		entityManager.flush();
		saveTrashed(userId, "/타파", DELETED_AT.plusDays(1));

		MvcResult secondPage = list(userId, "size", "2", "cursor", nextCursor(firstPage)).andReturn();

		assertThat(templateIds(body(secondPage))).containsExactly(oldest);
	}

	@ParameterizedTest
	@ValueSource(strings = {"!!!", "abc", "MjAyNi0xMC0wNw"})
	@DisplayName("해석할 수 없는 커서는 TRASH400_1")
	void invalidCursor(String cursor) throws Exception {
		expectError(list(userId, "cursor", cursor), 400, INVALID_PAGE_CODE, INVALID_PAGE_MESSAGE);
	}

	@ParameterizedTest
	@ValueSource(strings = {"0000-01-01T00:00:00.000000_1", "9999-12-31T23:59:59.999999_9223372036854775807"})
	@DisplayName("형식이 맞는 커서는 값이 극단이어도 DB 비교에서 실패하지 않는다")
	void extremeCursor(String decoded) throws Exception {
		String cursor = Base64.getUrlEncoder().withoutPadding()
			.encodeToString(decoded.getBytes(StandardCharsets.UTF_8));

		list(userId, "cursor", cursor).andExpect(status().isOk());
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1, 캐시 금지")
	void unauthenticated() throws Exception {
		expectError(mockMvc.perform(get(PATH)), 401, "COMMON401_1", "로그인이 필요해요.");
	}

	private ResultActions list(Long requesterId, String... params) throws Exception {
		MockHttpServletRequestBuilder request = get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(requesterId));
		for (int index = 0; index < params.length; index += 2) {
			request = request.param(params[index], params[index + 1]);
		}
		return mockMvc.perform(request);
	}

	private List<Long> collectPages(int size) throws Exception {
		List<Long> collected = new ArrayList<>();
		MvcResult page = list(userId, "size", String.valueOf(size)).andExpect(status().isOk()).andReturn();
		collected.addAll(templateIds(body(page)));
		while (nextCursor(page) != null) {
			page = list(userId, "size", String.valueOf(size), "cursor", nextCursor(page))
				.andExpect(status().isOk())
				.andReturn();
			collected.addAll(templateIds(body(page)));
		}
		return collected;
	}

	private static void expectError(ResultActions actions, int status, String code, String message) throws Exception {
		actions.andExpect(status().is(status))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	private static String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static String nextCursor(MvcResult result) throws Exception {
		return JsonPath.read(body(result), "$.result.nextCursor");
	}

	private static List<Long> templateIds(String json) {
		List<Number> ids = JsonPath.read(json, "$.result.templates[*].templateId");
		return ids.stream().map(Number::longValue).toList();
	}

	private static void assertFieldOrder(String json, List<String> fields) {
		List<Integer> positions = new ArrayList<>();
		for (String field : fields) {
			int position = json.indexOf("\"" + field + "\"");
			assertThat(position).as(field).isNotNegative();
			positions.add(position);
		}
		assertThat(positions).isSorted();
	}

	private Long saveTrashed(Long ownerId, String shortcut, LocalDateTime deletedAt) {
		Long id = templateRepository.saveAndFlush(TemplateFixture.trashedTemplate(ownerId, shortcut, deletedAt)).getId();
		entityManager.clear();
		return id;
	}

	/** 단축어 /t1, /t2 ... 로 같은 시각에 휴지통을 채운다 */
	private void saveTrashedCount(int count) {
		for (int number = 1; number <= count; number++) {
			saveTrashed(userId, "/t" + number, DELETED_AT);
		}
	}

	/** 복구 API(16번)가 아직 없어 행만 활성으로 되돌린다 */
	private void restore(Long templateId) {
		entityManager.createNativeQuery("update templates set deleted_at = null where id = :id")
			.setParameter("id", templateId)
			.executeUpdate();
		entityManager.clear();
	}

	private Long saveUser(String email) {
		User user = UserFixture.user(email);
		entityManager.persist(user);
		entityManager.flush();
		return user.getId();
	}

}
