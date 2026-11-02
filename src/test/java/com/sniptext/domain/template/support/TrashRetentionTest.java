package com.sniptext.domain.template.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrashRetentionTest {

	@Test
	@DisplayName("자동 영구 삭제 예정 시각은 삭제 시각의 30일 뒤다")
	void purgeAt() {
		LocalDateTime deletedAt = LocalDateTime.of(2026, 10, 7, 10, 58, 0, 123_456_000);

		assertThat(TrashRetention.purgeAt(deletedAt))
			.isEqualTo(LocalDateTime.of(2026, 11, 6, 10, 58, 0, 123_456_000));
	}

}
