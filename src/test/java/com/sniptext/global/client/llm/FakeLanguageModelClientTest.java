package com.sniptext.global.client.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeLanguageModelClientTest {

	private final FakeLanguageModelClient client = new FakeLanguageModelClient();

	@Test
	@DisplayName("<text>로 감싼 사용자 메시지는 태그와 앞뒤 줄바꿈을 벗긴 원문에 표시를 붙여 돌려준다")
	void unwrapsTextTag() {
		LanguageModelResult result = client.complete(request("<text>\n안녕하세요\n두 번째 줄\n</text>"));

		assertThat(result.text()).isEqualTo("[가짜 교정] 안녕하세요\n두 번째 줄")
			.doesNotContain("<text>", "</text>");
		assertThat(result.refused()).isFalse();
	}

	@Test
	@DisplayName("CRLF 줄바꿈도 앞뒤에서 벗기고, 원문 안의 앞뒤 공백은 그대로 둔다")
	void unwrapsCrlf() {
		assertThat(client.complete(request("<text>\r\n  원문  \r\n</text>")).text())
			.isEqualTo("[가짜 교정]   원문  ");
	}

	@Test
	@DisplayName("태그가 없으면 사용자 메시지를 그대로 쓴다")
	void withoutTag() {
		assertThat(client.complete(request("원문")).text()).isEqualTo("[가짜 교정] 원문");
	}

	private static LanguageModelRequest request(String userText) {
		return new LanguageModelRequest("시스템 지시", userText, 4000);
	}

}
