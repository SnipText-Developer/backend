package com.sniptext.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sniptext.domain.ai.entity.ToneStyle;
import com.sniptext.domain.ai.exception.AiErrorCode;
import com.sniptext.domain.ai.service.dto.command.AiToneCorrectCommand;
import com.sniptext.domain.ai.service.dto.result.AiToneCorrectResult;
import com.sniptext.domain.ai.service.dto.result.AiToneReserveResult;
import com.sniptext.domain.ai.service.dto.result.AiToneUsageResult;
import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.service.UserQueryService;
import com.sniptext.global.client.llm.LanguageModelClient;
import com.sniptext.global.client.llm.LanguageModelException;
import com.sniptext.global.client.llm.LanguageModelRequest;
import com.sniptext.global.client.llm.LanguageModelResult;
import com.sniptext.global.client.llm.LanguageModelTimeoutException;
import com.sniptext.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class AiToneCorrectionCommandServiceTest {

	private static final Long USER_ID = 7L;
	private static final LocalDate USAGE_DATE = LocalDate.of(2026, 10, 11);
	private static final String TEXT = "10월 14일까지 300만 원으로 진행 가능하다고 해줘";
	private static final AiToneCorrectCommand COMMAND = new AiToneCorrectCommand(USER_ID, ToneStyle.BOSS_EXTERNAL, TEXT);
	private static final AiToneReserveResult RESERVATION = new AiToneReserveResult(USER_ID, USAGE_DATE, 2);

	@Mock
	private UserQueryService userQueryService;

	@Mock
	private AiToneUsageCommandService aiToneUsageCommandService;

	@Mock
	private LanguageModelClient languageModelClient;

	@InjectMocks
	private AiToneCorrectionCommandService service;

	// 회원 확인에서 끝나는 테스트는 선점까지 가지 않으므로 lenient
	@BeforeEach
	void setUp() {
		lenient().when(aiToneUsageCommandService.reserve(USER_ID)).thenReturn(RESERVATION);
	}

	@Test
	@DisplayName("성공하면 앞뒤 공백을 지운 결과와 선점 결과로 만든 사용량을 돌려주고, 반환하지 않는다")
	void success() {
		givenModelReturns(" \n안녕하세요. 10월 14일까지 300만 원으로 진행 가능합니다.\n감사합니다. \n", false);

		AiToneCorrectResult result = service.correct(COMMAND);

		assertThat(result.correctedText()).isEqualTo("안녕하세요. 10월 14일까지 300만 원으로 진행 가능합니다.\n감사합니다.");
		assertThat(result.style()).isEqualTo(ToneStyle.BOSS_EXTERNAL);
		assertThat(result.keyInfoDiff().hasDifference()).isFalse();
		assertThat(result.usage()).isEqualTo(new AiToneUsageResult(2, 5, 3, LocalDateTime.of(2026, 10, 12, 0, 0)));
		verify(aiToneUsageCommandService, never()).release(any());
	}

	@Test
	@DisplayName("언어 모델에 문체 지시문, <text>로 감싼 원문, 출력 토큰 상한 6000을 보낸다")
	void request() {
		givenModelReturns("교정 결과", false);

		service.correct(COMMAND);

		ArgumentCaptor<LanguageModelRequest> captor = ArgumentCaptor.forClass(LanguageModelRequest.class);
		verify(languageModelClient).complete(captor.capture());
		LanguageModelRequest request = captor.getValue();
		assertThat(request.systemPrompt()).isEqualTo(ToneCorrectionPrompt.systemPrompt(ToneStyle.BOSS_EXTERNAL));
		assertThat(request.userText()).isEqualTo(ToneCorrectionPrompt.userMessage(TEXT));
		assertThat(request.maxOutputTokens()).isEqualTo(6000);
	}

	@Test
	@DisplayName("날짜·금액이 빠지거나 바뀌어도 정상 결과이고, 차이를 담으며 반환하지 않는다")
	void keyInfoDifference() {
		givenModelReturns("안녕하세요. 10월 15일까지 진행 가능합니다.", false);

		AiToneCorrectResult result = service.correct(COMMAND);

		assertThat(result.keyInfoDiff().missingValues()).containsExactly("10월 14일", "300만 원");
		assertThat(result.keyInfoDiff().addedValues()).containsExactly("10월 15일");
		verify(aiToneUsageCommandService, never()).release(any());
	}

	@Test
	@DisplayName("없는 회원이면 USER404_1이고 선점·언어 모델 호출을 하지 않는다")
	void missingUser() {
		willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND)).given(userQueryService).validateActive(USER_ID);

		assertErrorCode(UserErrorCode.USER_NOT_FOUND.getCode());

		verify(aiToneUsageCommandService, never()).reserve(any());
		verifyNoInteractions(languageModelClient);
	}

	@Test
	@DisplayName("남은 횟수가 없으면 AI429_1이고 언어 모델을 부르지 않으며 반환하지 않는다")
	void dailyLimitExceeded() {
		given(aiToneUsageCommandService.reserve(USER_ID))
			.willThrow(new BusinessException(AiErrorCode.DAILY_LIMIT_EXCEEDED));

		assertErrorCode("AI429_1");

		verifyNoInteractions(languageModelClient);
		verify(aiToneUsageCommandService, never()).release(any());
	}

	@Test
	@DisplayName("모델이 거절(refused)하면 AI422_1이고 선점한 1회를 반환한다")
	void refused() {
		givenModelReturns("", true);

		assertErrorCode("AI422_1");

		verify(aiToneUsageCommandService).release(RESERVATION);
	}

	@ParameterizedTest
	@ValueSource(strings = {"[[SNIPTEXT_BLOCKED]]", " \n[[SNIPTEXT_BLOCKED]]\n ", "﻿[[SNIPTEXT_BLOCKED]] "})
	@DisplayName("표식만 출력하면(앞뒤 공백 무시) AI422_1이고 선점한 1회를 반환한다")
	void blockedMarker(String modelText) {
		givenModelReturns(modelText, false);

		assertErrorCode("AI422_1");

		verify(aiToneUsageCommandService).release(RESERVATION);
	}

	@Test
	@DisplayName("외부 오류면 AI502_1이고 선점한 1회를 반환한다")
	void languageModelFailed() {
		given(languageModelClient.complete(any())).willThrow(new LanguageModelException("언어 모델 HTTP 오류: 529"));

		assertErrorCode("AI502_1");

		verify(aiToneUsageCommandService).release(RESERVATION);
	}

	@Test
	@DisplayName("응답 시간 초과면 AI504_1이고 선점한 1회를 반환한다")
	void languageModelTimeout() {
		given(languageModelClient.complete(any())).willThrow(new LanguageModelTimeoutException("언어 모델 응답 시간 초과"));

		assertErrorCode("AI504_1");

		verify(aiToneUsageCommandService).release(RESERVATION);
	}

	@Test
	@DisplayName("반환이 실패해도 원래 에러 코드를 그대로 던진다")
	void releaseFails() {
		given(languageModelClient.complete(any())).willThrow(new LanguageModelTimeoutException("언어 모델 응답 시간 초과"));
		willThrow(new IllegalStateException("DB 오류")).given(aiToneUsageCommandService).release(RESERVATION);

		assertErrorCode("AI504_1");
	}

	@Test
	@DisplayName("그 밖의 예상 못 한 예외는 그대로 올라가고 반환하지 않는다")
	void unexpectedException() {
		given(languageModelClient.complete(any())).willThrow(new IllegalStateException("예상 못 한 오류"));

		assertThatThrownBy(() -> service.correct(COMMAND)).isInstanceOf(IllegalStateException.class);

		verify(aiToneUsageCommandService, never()).release(any());
	}

	@Test
	@DisplayName("명령 객체의 toString에는 원문 대신 길이만 나온다")
	void commandToStringHidesText() {
		assertThat(COMMAND.toString()).doesNotContain(TEXT).contains("textLength=" + TEXT.length());
	}

	@Test
	@DisplayName("결과 객체의 toString에는 교정 결과와 핵심 정보 값이 나오지 않는다")
	void resultToStringHidesText() {
		givenModelReturns("안녕하세요. 10월 15일까지 진행 가능합니다.", false);

		String described = service.correct(COMMAND).toString();

		assertThat(described).doesNotContain("안녕하세요", "10월 14일", "10월 15일", "300만 원");
		for (String expected : List.of("correctedTextLength=", "missing=2", "added=1")) {
			assertThat(described).contains(expected);
		}
	}

	private void givenModelReturns(String text, boolean refused) {
		given(languageModelClient.complete(any())).willReturn(new LanguageModelResult(text, refused, 100));
	}

	private void assertErrorCode(String code) {
		assertThatThrownBy(() -> service.correct(COMMAND))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getResultCode().getCode())
			.isEqualTo(code);
	}

}
