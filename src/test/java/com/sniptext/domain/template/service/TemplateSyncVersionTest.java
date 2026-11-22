package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemplateSyncVersionTest {

	@Test
	@DisplayName("버전은 v1.{휴지통 제외 개수}.{가장 늦은 수정 시각의 에폭 마이크로초}이고 시각은 한국 시간 기준이다")
	void format() {
		// 2024-11-15T20:11:01 KST = 2024-11-15T11:11:01Z = 에폭 1731669061초 (명세 예시 값)
		LocalDateTime latest = LocalDateTime.of(2024, 11, 15, 20, 11, 1);

		assertThat(TemplateSyncVersion.of(18, latest)).isEqualTo("v1.18.1731669061000000");
	}

	@Test
	@DisplayName("시각은 DATETIME(6)과 같은 마이크로초까지 넣어 같은 초 안의 변경도 구분한다")
	void microseconds() {
		LocalDateTime latest = LocalDateTime.of(2024, 11, 15, 20, 11, 1, 123_456_000);

		assertThat(TemplateSyncVersion.of(1, latest)).isEqualTo("v1.1.1731669061123456");
		assertThat(TemplateSyncVersion.of(1, latest.plusNanos(1_000)))
			.isEqualTo("v1.1.1731669061123457");
	}

	@Test
	@DisplayName("템플릿이 하나도 없으면(가장 늦은 시각 없음) v1.0.0이다")
	void empty() {
		assertThat(TemplateSyncVersion.of(0, null)).isEqualTo("v1.0.0");
	}

	@Test
	@DisplayName("시각이 같아도 개수가 다르면 버전이 다르다")
	void countMatters() {
		LocalDateTime latest = LocalDateTime.of(2026, 11, 15, 20, 31, 10);

		assertThat(TemplateSyncVersion.of(2, latest)).isNotEqualTo(TemplateSyncVersion.of(3, latest));
	}

}
