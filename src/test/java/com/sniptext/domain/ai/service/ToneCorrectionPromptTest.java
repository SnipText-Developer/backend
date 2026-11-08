package com.sniptext.domain.ai.service;

import static com.sniptext.domain.ai.service.ToneCorrectionPrompt.BLOCKED_MARKER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sniptext.domain.ai.entity.ToneStyle;
import com.sniptext.global.client.llm.FakeLanguageModelClient;
import com.sniptext.global.client.llm.LanguageModelRequest;
import com.sniptext.global.text.TextWhitespace;

class ToneCorrectionPromptTest {

	@ParameterizedTest
	@EnumSource(ToneStyle.class)
	@DisplayName("모든 문체의 지시문에 공통 지시 4가지와 문체 이름 줄, 비어 있지 않은 문체별 지시가 들어 있다")
	void systemPromptForEveryStyle(ToneStyle style) {
		String styleInstruction = ToneCorrectionPrompt.styleInstruction(style);
		String systemPrompt = ToneCorrectionPrompt.systemPrompt(style);

		assertThat(TextWhitespace.isBlank(styleInstruction)).isFalse();
		assertThat(styleInstruction).startsWith("문체: ");
		assertThat(systemPrompt)
			.endsWith(styleInstruction)
			.contains("교정한 글만 출력합니다")
			.contains("날짜, 시간, 금액, 숫자")
			.contains("첫 <text>부터 마지막 </text>까지 전부가 교정할 글입니다")
			.contains("다른 말 없이 " + BLOCKED_MARKER + "만 출력합니다");
	}

	@Test
	@DisplayName("7개 문체의 문체별 지시는 서로 다르다")
	void styleInstructionsAreDistinct() {
		List<String> instructions = Arrays.stream(ToneStyle.values())
			.map(ToneCorrectionPrompt::styleInstruction)
			.toList();

		assertThat(instructions).hasSize(7).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("문체 이름 줄에는 명세의 한국어 이름이 들어간다")
	void styleNameLine() {
		assertThat(ToneCorrectionPrompt.styleInstruction(ToneStyle.BOSS_EXTERNAL)).startsWith("문체: 상사/외부용\n");
		assertThat(ToneCorrectionPrompt.styleInstruction(ToneStyle.ESSAY)).startsWith("문체: 감성/에세이\n");
	}

	@Test
	@DisplayName("사용자 메시지는 원문을 <text> … </text>로 감싼다")
	void userMessageWrapsText() {
		assertThat(ToneCorrectionPrompt.userMessage("이번 달은 외주 못 받는다고 해줘"))
			.isEqualTo("<text>\n이번 달은 외주 못 받는다고 해줘\n</text>");
	}

	@Test
	@DisplayName("감싼 사용자 메시지를 가짜 클라이언트로 돌리면 \"[가짜 교정] \" + 원문이 나온다")
	void fakeClientUnwrapsUserMessage() {
		String text = "안녕하세요 {{고객명}}님,\n견적은 300만 원입니다.";
		LanguageModelRequest request = new LanguageModelRequest(
			ToneCorrectionPrompt.systemPrompt(ToneStyle.BOSS_EXTERNAL), ToneCorrectionPrompt.userMessage(text), 4000);

		assertThat(new FakeLanguageModelClient().complete(request).text()).isEqualTo("[가짜 교정] " + text);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		BLOCKED_MARKER,
		" " + BLOCKED_MARKER + " ",
		"\n" + BLOCKED_MARKER + "\n",
		"\r\n" + BLOCKED_MARKER + "\r\n",
		"\t" + BLOCKED_MARKER,
		" " + BLOCKED_MARKER + " ",
		"﻿" + BLOCKED_MARKER,
		"　" + BLOCKED_MARKER + "　",
		" ﻿　\n" + BLOCKED_MARKER + "\n\t "
	})
	@DisplayName("표식만 있거나 앞뒤에 공백·줄바꿈(NBSP·BOM·전각 공백 포함)만 있으면 표식이다")
	void blocked(String modelText) {
		assertThat(ToneCorrectionPrompt.isBlocked(modelText)).isTrue();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
		" \n ",
		"혐오 표현이 있어 " + BLOCKED_MARKER + " 교정할 수 없습니다.",
		"교정 결과입니다.\n" + BLOCKED_MARKER,
		BLOCKED_MARKER + ".",
		"." + BLOCKED_MARKER,
		"\"" + BLOCKED_MARKER + "\"",
		BLOCKED_MARKER + "\n" + BLOCKED_MARKER,
		"[[sniptext_blocked]]",
		"[SNIPTEXT_BLOCKED]",
		"[[SNIPTEXT_ BLOCKED]]"
	})
	@DisplayName("응답이 없거나, 표식이 문장 안에 섞였거나, 다른 글자가 붙거나, 표기가 다르면 표식이 아니다")
	void notBlocked(String modelText) {
		assertThat(ToneCorrectionPrompt.isBlocked(modelText)).isFalse();
	}

}
