package com.sniptext.domain.template.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sniptext.global.policy.UsageLimits;

class TemplateLimitTest {

	private static final int LIMIT = UsageLimits.TEMPLATE_LIMIT;

	@Test
	@DisplayName("한도 1개 전(49개)은 도달이 아니고 1개 남는다")
	void belowLimit() {
		assertThat(TemplateLimit.isReached(LIMIT - 1)).isFalse();
		assertThat(TemplateLimit.remaining(LIMIT - 1)).isEqualTo(1);
	}

	@Test
	@DisplayName("한도(50개)면 도달이고 0개 남는다")
	void atLimit() {
		assertThat(TemplateLimit.isReached(LIMIT)).isTrue();
		assertThat(TemplateLimit.remaining(LIMIT)).isZero();
	}

	@Test
	@DisplayName("한도를 넘어도(51개) 도달이고 남은 개수는 음수가 아니라 0이다")
	void overLimit() {
		assertThat(TemplateLimit.isReached(LIMIT + 1)).isTrue();
		assertThat(TemplateLimit.remaining(LIMIT + 1)).isZero();
	}

	@Test
	@DisplayName("0개면 한도만큼 남는다")
	void empty() {
		assertThat(TemplateLimit.isReached(0)).isFalse();
		assertThat(TemplateLimit.remaining(0)).isEqualTo(LIMIT);
	}

}
