package com.sniptext.domain.user.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.ai.repository.AiToneDailyUsageRepository;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.policy.UsageLimits;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

/** 명세 6번. 실제 토큰 필터와 template·ai 서비스, MySQL까지 거쳐 확인한다. */
@Transactional
@Import(MutableClockConfiguration.class)
class UserUsageControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/users/me/usage";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 11, 6, 10, 0);
	private static final LocalDate TODAY = FIXED.toLocalDate();

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private AiToneDailyUsageRepository aiToneDailyUsageRepository;

	@Autowired
	private MutableClock clock;

	private Long userId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
		userId = userRepository.save(UserFixture.user("usage@gmail.com")).getId();
	}

	@Test
	@DisplayName("템플릿과 AI 사용 기록이 없으면 0개·0회이고, 다음 날 자정을 +09:00으로 내려주며 캐시하지 않는다")
	void noUsage() throws Exception {
		getUsage(userId)
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.code").value("USER200_2"))
			.andExpect(jsonPath("$.message").value("사용량 조회에 성공했습니다."))
			.andExpect(jsonPath("$.result.template.count").value(0))
			.andExpect(jsonPath("$.result.template.limit").value(50))
			.andExpect(jsonPath("$.result.template.remaining").value(50))
			.andExpect(jsonPath("$.result.template.isFull").value(false))
			.andExpect(jsonPath("$.result.template.full").doesNotExist())
			.andExpect(jsonPath("$.result.aiTone.usedCount").value(0))
			.andExpect(jsonPath("$.result.aiTone.limit").value(5))
			.andExpect(jsonPath("$.result.aiTone.remaining").value(5))
			.andExpect(jsonPath("$.result.aiTone.resetAt").value("2026-11-07T00:00:00+09:00"))
			.andExpect(jsonPath("$.result.plan").doesNotExist())
			.andExpect(jsonPath("$.result.aiTone.available").doesNotExist());
	}

	@Test
	@DisplayName("템플릿 49개면 1개 남고 한도 도달이 아니다")
	void templateBelowLimit() throws Exception {
		saveTemplates(userId, UsageLimits.TEMPLATE_LIMIT - 1);

		getUsage(userId)
			.andExpect(jsonPath("$.result.template.count").value(49))
			.andExpect(jsonPath("$.result.template.remaining").value(1))
			.andExpect(jsonPath("$.result.template.isFull").value(false));
	}

	@Test
	@DisplayName("템플릿 50개면 0개 남고 한도 도달이다")
	void templateAtLimit() throws Exception {
		saveTemplates(userId, UsageLimits.TEMPLATE_LIMIT);

		getUsage(userId)
			.andExpect(jsonPath("$.result.template.count").value(50))
			.andExpect(jsonPath("$.result.template.remaining").value(0))
			.andExpect(jsonPath("$.result.template.isFull").value(true));
	}

	@Test
	@DisplayName("휴지통 템플릿과 다른 회원의 템플릿은 세지 않는다")
	void countsOnlyMyActiveTemplates() throws Exception {
		Long otherId = userRepository.save(UserFixture.user("other@gmail.com")).getId();
		saveTemplates(userId, 2);
		templateRepository.save(TemplateFixture.trashedTemplate(userId, "/휴지통", FIXED));
		saveTemplates(otherId, 3);

		getUsage(userId).andExpect(jsonPath("$.result.template.count").value(2));
	}

	@Test
	@DisplayName("AI 톤 교정을 오늘 5회 썼으면 남은 횟수가 0이다")
	void aiToneAtLimit() throws Exception {
		useAiTone(userId, TODAY, UsageLimits.AI_TONE_DAILY_LIMIT);

		getUsage(userId)
			.andExpect(jsonPath("$.result.aiTone.usedCount").value(5))
			.andExpect(jsonPath("$.result.aiTone.remaining").value(0));
	}

	@Test
	@DisplayName("어제 사용량과 다른 회원의 사용량은 세지 않는다")
	void countsOnlyMyTodayAiTone() throws Exception {
		Long otherId = userRepository.save(UserFixture.user("other@gmail.com")).getId();
		useAiTone(userId, TODAY, 2);
		useAiTone(userId, TODAY.minusDays(1), 3);
		useAiTone(otherId, TODAY, 4);

		getUsage(userId)
			.andExpect(jsonPath("$.result.aiTone.usedCount").value(2))
			.andExpect(jsonPath("$.result.aiTone.remaining").value(3));
	}

	@Test
	@DisplayName("탈퇴한 회원의 토큰은 USER404_1이다")
	void deletedUser() throws Exception {
		Long deletedId = userRepository.save(UserFixture.deletedUser("deleted@gmail.com", FIXED)).getId();

		getUsage(deletedId)
			.andExpect(status().isNotFound())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.code").value("USER404_1"))
			.andExpect(jsonPath("$.result").value(nullValue()));
	}

	@Test
	@DisplayName("없는 회원의 토큰은 USER404_1이다")
	void unknownUser() throws Exception {
		getUsage(Long.MAX_VALUE)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("USER404_1"));
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1이다")
	void withoutToken() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("COMMON401_1"));
	}

	private ResultActions getUsage(Long id) throws Exception {
		return mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(id)));
	}

	private void saveTemplates(Long ownerId, int count) {
		for (int i = 1; i <= count; i++) {
			templateRepository.save(TemplateFixture.template(ownerId, "/견적" + i));
		}
	}

	private void useAiTone(Long ownerId, LocalDate date, int times) {
		aiToneDailyUsageRepository.insertIfAbsent(ownerId, date, FIXED);
		for (int i = 0; i < times; i++) {
			aiToneDailyUsageRepository.incrementIfBelow(ownerId, date, UsageLimits.AI_TONE_DAILY_LIMIT, FIXED);
		}
	}

}
