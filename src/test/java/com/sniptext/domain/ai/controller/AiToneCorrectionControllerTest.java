package com.sniptext.domain.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.sniptext.domain.ai.repository.AiToneDailyUsageRepository;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.client.llm.LanguageModelClient;
import com.sniptext.global.client.llm.LanguageModelException;
import com.sniptext.global.client.llm.LanguageModelResult;
import com.sniptext.global.client.llm.LanguageModelTimeoutException;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import tools.jackson.databind.json.JsonMapper;

/**
 * 명세 22번. 실제 필터·MySQL을 거치고, 언어 모델은 가짜 구현을 감싼 스파이로 실패를 만든다.
 * 클래스에 @Transactional을 붙이지 않는다. 서비스가 트랜잭션 밖에서 선점·반환을 부르는지까지 확인해야 한다
 * (선점·반환은 호출자 트랜잭션 안에서 부르면 예외). 대신 끝나면 만든 사용량과 회원을 지운다.
 */
@Import(MutableClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AiToneCorrectionControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/ai/tone-corrections";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 23, 59, 30);
	private static final LocalDate TODAY = NOW.toLocalDate();
	/** 로그 검사용으로 다른 곳에 나오지 않을 문장을 쓴다 */
	private static final String TEXT = "원문비밀문장 11월 30일까지 외주 못 받는다고 해줘";
	private static final String CORRECTED = "결과비밀문장 외주를 받기 어렵습니다.";
	private static final List<String> FIELD_ORDER = List.of("correctedText", "style", "keyInfoWarning", "usage");

	@MockitoSpyBean
	private LanguageModelClient languageModelClient;

	@Autowired
	private AiToneDailyUsageRepository aiToneDailyUsageRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JsonMapper jsonMapper;

	private Long userId;

	@BeforeEach
	void setUp() {
		clock.setTo(NOW);
		userId = userRepository.save(UserFixture.user("tone@sniptext.com")).getId();
	}

	@AfterEach
	void tearDown() {
		aiToneDailyUsageRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("성공하면 200 AI200_1, 명세 순서의 필드, 선점 후 사용량과 다음 날 00:00+09:00 리셋 시각, 캐시 금지")
	void success() throws Exception {
		MvcResult result = correct(userId, "BOSS_EXTERNAL", TEXT)
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("AI200_1"))
			.andExpect(jsonPath("$.message").value("톤 교정에 성공했습니다."))
			.andExpect(jsonPath("$.result.correctedText").value("[가짜 교정] " + TEXT))
			.andExpect(jsonPath("$.result.style").value("BOSS_EXTERNAL"))
			.andExpect(jsonPath("$.result.keyInfoWarning").isEmpty())
			.andExpect(jsonPath("$.result.usage.usedCount").value(1))
			.andExpect(jsonPath("$.result.usage.limit").value(5))
			.andExpect(jsonPath("$.result.usage.remaining").value(4))
			.andExpect(jsonPath("$.result.usage.resetAt").value("2026-10-12T00:00:00+09:00"))
			.andReturn();

		assertFieldOrder(body(result));
		assertThat(usedCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("날짜가 빠지거나 바뀌면 정상 응답에 명세 문구의 keyInfoWarning이 채워지고 횟수는 차감된 채다")
	void keyInfoWarning() throws Exception {
		doReturn(new LanguageModelResult("12월 1일까지 " + CORRECTED, false, 50)).when(languageModelClient).complete(any());

		correct(userId, "REPORT_OFFICIAL", TEXT)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("AI200_1"))
			.andExpect(jsonPath("$.result.keyInfoWarning.message")
				.value("원문의 일부 정보가 빠지거나 변경되었을 수 있어요. 확인 후 사용해주세요."))
			.andExpect(jsonPath("$.result.keyInfoWarning.missingValues[0]").value("11월 30일"))
			.andExpect(jsonPath("$.result.keyInfoWarning.addedValues[0]").value("12월 1일"));

		assertThat(usedCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("5회 사용 후 6번째는 AI429_1이고 언어 모델을 부르지 않는다")
	void sixthRequest() throws Exception {
		for (int i = 1; i <= 5; i++) {
			correct(userId, "CASUAL", TEXT).andExpect(jsonPath("$.result.usage.remaining").value(5 - i));
		}
		clearInvocations(languageModelClient);

		expectError(correct(userId, "CASUAL", TEXT), 429, "AI429_1", "오늘 톤 교정 횟수를 모두 사용했어요.");

		verify(languageModelClient, never()).complete(any());
		assertThat(usedCount()).isEqualTo(5);
	}

	@Test
	@DisplayName("거절·표식이면 AI422_1, 외부 오류면 AI502_1, 시간 초과면 AI504_1이고 사용량은 요청 전 값으로 돌아간다")
	void failuresRelease() throws Exception {
		correct(userId, "CASUAL", TEXT).andExpect(status().isOk());

		doReturn(new LanguageModelResult("", true, 10)).when(languageModelClient).complete(any());
		expectError(correct(userId, "CASUAL", TEXT), 422, "AI422_1", "부적절한 표현이 있어 교정할 수 없어요. 수정 후 다시 시도해 주세요.");
		assertThat(usedCount()).isEqualTo(1);

		doReturn(new LanguageModelResult("\n[[SNIPTEXT_BLOCKED]]\n", false, 21)).when(languageModelClient).complete(any());
		expectError(correct(userId, "CASUAL", TEXT), 422, "AI422_1", "부적절한 표현이 있어 교정할 수 없어요. 수정 후 다시 시도해 주세요.");
		assertThat(usedCount()).isEqualTo(1);

		doThrow(new LanguageModelException("언어 모델 출력 토큰 상한 도달")).when(languageModelClient).complete(any());
		expectError(correct(userId, "CASUAL", TEXT), 502, "AI502_1", "일시적인 오류로 요청을 처리하지 못했어요. 다시 한 번 물어봐 주시겠어요?");
		assertThat(usedCount()).isEqualTo(1);

		doThrow(new LanguageModelTimeoutException("언어 모델 응답 시간 초과")).when(languageModelClient).complete(any());
		expectError(correct(userId, "CASUAL", TEXT), 504, "AI504_1", "일시적인 오류로 요청을 처리하지 못했어요. 다시 한 번 물어봐 주시겠어요?");
		assertThat(usedCount()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "ESSAY", "casual", "UNKNOWN"})
	@DisplayName("style이 비었거나 없는 값·소문자면 AI400_1 (COMMON400_1이 아님)이고 사용량 행이 생기지 않는다")
	void invalidStyle(String style) throws Exception {
		expectError(correct(userId, style, TEXT), 400, "AI400_1", "문체를 선택해주세요.");
		assertThat(aiToneDailyUsageRepository.count()).isZero();
	}

	@Test
	@DisplayName("style·text가 없으면 style 오류(AI400_1)가 먼저이고, text만 없거나 공백만이면 AI400_2")
	void missingFields() throws Exception {
		expectError(request(Map.of()), 400, "AI400_1", "문체를 선택해주세요.");
		expectError(request(Map.of("style", "CASUAL")), 400, "AI400_2", "교정할 문장을 입력해주세요.");
		expectError(correct(userId, "CASUAL", "  ﻿　\n"), 400, "AI400_2", "교정할 문장을 입력해주세요.");
		assertThat(aiToneDailyUsageRepository.count()).isZero();
	}

	@Test
	@DisplayName("text는 3000자까지 통과하고 3001자면 AI400_3")
	void textLength() throws Exception {
		correct(userId, "CASUAL", "가".repeat(3000)).andExpect(status().isOk());
		expectError(correct(userId, "CASUAL", "가".repeat(3001)), 400, "AI400_3", "문장은 3,000자까지 입력할 수 있어요.");
		assertThat(usedCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("없는 회원의 토큰이면 USER404_1이고 사용량 행이 생기지 않는다 (500이 아님)")
	void missingUser() throws Exception {
		expectError(correct(-1L, "CASUAL", TEXT), 404, "USER404_1", "회원 정보를 찾을 수 없어요.");
		assertThat(aiToneDailyUsageRepository.count()).isZero();
	}

	@Test
	@DisplayName("토큰 없이 요청하면 401 COMMON401_1")
	void unauthenticated() throws Exception {
		mockMvc.perform(post(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(Map.of("style", "CASUAL", "text", TEXT))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("COMMON401_1"));
	}

	@Test
	@DisplayName("성공·422·502·504·예상 못 한 500 모두 로그에 원문·결과가 없고, 소요 시간·출력 토큰 수는 남는다")
	void logsHideText(CapturedOutput output) throws Exception {
		doReturn(new LanguageModelResult(CORRECTED, false, 4321)).when(languageModelClient).complete(any());
		correct(userId, "CASUAL", TEXT).andExpect(status().isOk());

		doReturn(new LanguageModelResult("[[SNIPTEXT_BLOCKED]]", false, 21)).when(languageModelClient).complete(any());
		correct(userId, "CASUAL", TEXT).andExpect(status().is(422));

		doThrow(new LanguageModelException("언어 모델 HTTP 오류: 529")).when(languageModelClient).complete(any());
		correct(userId, "CASUAL", TEXT).andExpect(status().is(502));

		doThrow(new LanguageModelTimeoutException("언어 모델 응답 시간 초과")).when(languageModelClient).complete(any());
		correct(userId, "CASUAL", TEXT).andExpect(status().is(504));

		doThrow(new IllegalStateException("예상 못 한 오류")).when(languageModelClient).complete(any());
		correct(userId, "CASUAL", TEXT).andExpect(status().is(500));

		assertThat(output.getAll())
			.doesNotContain("원문비밀문장", "결과비밀문장")
			.contains("outcome=success", "outputTokens=4321", "outcome=blocked", "elapsedMs=");
	}

	private ResultActions correct(Long requestUserId, String style, String text) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("style", style);
		body.put("text", text);
		return request(requestUserId, body);
	}

	private ResultActions request(Map<String, Object> body) throws Exception {
		return request(userId, body);
	}

	private ResultActions request(Long requestUserId, Map<String, Object> body) throws Exception {
		return mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearer(requestUserId))
			.contentType(MediaType.APPLICATION_JSON)
			.content(jsonMapper.writeValueAsString(body)));
	}

	private static void expectError(ResultActions actions, int status, String code, String message) throws Exception {
		actions.andExpect(status().is(status))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	private int usedCount() {
		return aiToneDailyUsageRepository.findUsedCount(userId, TODAY).orElse(0);
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
