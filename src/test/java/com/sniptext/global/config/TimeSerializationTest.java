package com.sniptext.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import com.sniptext.support.IntegrationTestSupport;

@WithMockUser
class TimeSerializationTest extends IntegrationTestSupport {

	@Autowired
	private Clock clock;

	@Test
	@DisplayName("Clock 빈은 한국 시간대다")
	void clockZone() {
		assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
	}

	@Test
	@DisplayName("응답의 LocalDateTime은 +09:00을 붙여 초 단위까지, 다른 시간대 OffsetDateTime은 한국 시간으로, 날짜는 그대로 내보낸다")
	void responseFormat() throws Exception {
		mockMvc.perform(get("/api/v1/test/times"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.result.localDateTime").value("2026-10-07T10:15:00+09:00"))
			.andExpect(jsonPath("$.result.offsetDateTime").value("2026-10-07T10:15:00+09:00"))
			.andExpect(jsonPath("$.result.date").value("2026-10-14"));
	}

	@Test
	@DisplayName("요청의 시각은 시간대 표시가 있으면 같은 순간의 한국 시간으로 받는다")
	void requestWithOffset() throws Exception {
		postTimes("{\"localDateTime\":\"2026-10-07T01:15:00Z\",\"offsetDateTime\":\"2026-10-07T10:15:00+09:00\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.localDateTime").value("2026-10-07T10:15:00+09:00"))
			.andExpect(jsonPath("$.result.offsetDateTime").value("2026-10-07T10:15:00+09:00"));
	}

	@Test
	@DisplayName("요청의 시각에 시간대 표시가 없으면 COMMON400_1로 응답한다")
	void requestWithoutOffset() throws Exception {
		postTimes("{\"localDateTime\":\"2026-10-07T10:15:00\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON400_1"));
		postTimes("{\"offsetDateTime\":\"2026-10-07T10:15:00\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON400_1"));
	}

	private ResultActions postTimes(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/test/times").contentType(MediaType.APPLICATION_JSON).content(body));
	}

}
