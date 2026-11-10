package com.sniptext.domain.ai.service;

import org.springframework.stereotype.Service;

import com.sniptext.domain.ai.exception.AiErrorCode;
import com.sniptext.domain.ai.service.dto.command.AiToneCorrectCommand;
import com.sniptext.domain.ai.service.dto.result.AiToneCorrectResult;
import com.sniptext.domain.ai.service.dto.result.AiToneReserveResult;
import com.sniptext.domain.ai.service.dto.result.AiToneUsageResult;
import com.sniptext.domain.ai.support.KeyInfoChecker;
import com.sniptext.domain.user.service.UserQueryService;
import com.sniptext.global.client.llm.LanguageModelClient;
import com.sniptext.global.client.llm.LanguageModelException;
import com.sniptext.global.client.llm.LanguageModelRequest;
import com.sniptext.global.client.llm.LanguageModelResult;
import com.sniptext.global.client.llm.LanguageModelTimeoutException;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.global.policy.UsageLimits;
import com.sniptext.global.text.TextWhitespace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 톤 교정 (명세 22번). 1회를 먼저 잡고 언어 모델을 부른 뒤, 차단·외부 오류·시간 초과면 되돌린다.
 * <p>
 * 클래스·메서드 모두 트랜잭션을 걸지 않는다 ("명령 서비스는 쓰기 메서드마다 @Transactional" 규칙의 예외).
 * 외부 호출(최대 20초) 동안 DB 커넥션을 쥐지 않도록, 선점·반환이 각자 짧은 트랜잭션으로 끝난다
 * (AiToneUsageCommandService는 호출자 트랜잭션 안에서 부르면 예외를 던진다).
 * <p>
 * 원문과 결과는 저장하지도 로그에 남기지도 않는다. 로그에는 회원 번호·문체·결과 종류·소요 시간·출력 토큰 수만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiToneCorrectionCommandService {

	/** 출력 토큰 상한 (비용 상한). 생각 토큰도 포함되며, 3000자 원문 보고/공문 교정이 4000에서는 잘렸다 (#64 확인) */
	static final int MAX_OUTPUT_TOKENS = 6000;

	private final UserQueryService userQueryService;
	private final AiToneUsageCommandService aiToneUsageCommandService;
	private final LanguageModelClient languageModelClient;

	public AiToneCorrectResult correct(AiToneCorrectCommand command) {
		userQueryService.validateActive(command.userId());
		AiToneReserveResult reservation = aiToneUsageCommandService.reserve(command.userId());

		LanguageModelResult modelResult = complete(command, reservation);

		String correctedText = TextWhitespace.strip(modelResult.text());
		return new AiToneCorrectResult(correctedText, command.style(),
			KeyInfoChecker.compare(command.text(), correctedText), usage(reservation));
	}

	/** 언어 모델을 부른다. 차단·외부 오류·시간 초과면 선점한 1회를 되돌리고 에러를 던진다. 반환은 이 세 경우에서만 한다. */
	private LanguageModelResult complete(AiToneCorrectCommand command, AiToneReserveResult reservation) {
		LanguageModelRequest request = new LanguageModelRequest(ToneCorrectionPrompt.systemPrompt(command.style()),
			ToneCorrectionPrompt.userMessage(command.text()), MAX_OUTPUT_TOKENS);
		long startedAt = System.nanoTime();
		LanguageModelResult result;
		try {
			result = languageModelClient.complete(request);
		} catch (LanguageModelTimeoutException e) {
			logFailure(command, startedAt, e);
			throw releaseAndFail(reservation, AiErrorCode.LANGUAGE_MODEL_TIMEOUT);
		} catch (LanguageModelException e) {
			logFailure(command, startedAt, e);
			throw releaseAndFail(reservation, AiErrorCode.LANGUAGE_MODEL_FAILED);
		}

		boolean blocked = result.refused() || ToneCorrectionPrompt.isBlocked(result.text());
		log.info("AI 톤 교정 userId={} style={} outcome={} elapsedMs={} outputTokens={}", command.userId(),
			command.style(), blocked ? "blocked" : "success", elapsedMillis(startedAt), result.outputTokens());
		if (blocked) {
			throw releaseAndFail(reservation, AiErrorCode.INAPPROPRIATE_TEXT);
		}
		return result;
	}

	/** 반환이 실패해도 원래 에러를 그대로 돌려준다. 로그에는 회원 번호·날짜와 예외 종류만 남긴다. */
	private BusinessException releaseAndFail(AiToneReserveResult reservation, AiErrorCode errorCode) {
		try {
			aiToneUsageCommandService.release(reservation);
		} catch (RuntimeException e) {
			log.error("AI 톤 교정 사용량 반환 실패 userId={} usageDate={} exception={}", reservation.userId(),
				reservation.usageDate(), e.getClass().getSimpleName());
		}
		return new BusinessException(errorCode);
	}

	// 언어 모델 예외 메시지는 상태 코드·오류 종류만 담는다 (원문·응답 본문 없음)
	private static void logFailure(AiToneCorrectCommand command, long startedAt, RuntimeException e) {
		log.warn("AI 톤 교정 실패 userId={} style={} elapsedMs={} reason={}", command.userId(), command.style(),
			elapsedMillis(startedAt), e.getMessage());
	}

	/** 다시 조회하지 않고 선점 결과로 만든다. 리셋 시각은 선점한 날짜의 다음 날 00:00(KST)이다. */
	private static AiToneUsageResult usage(AiToneReserveResult reservation) {
		int limit = UsageLimits.AI_TONE_DAILY_LIMIT;
		int usedCount = reservation.usedCountAfter();
		return new AiToneUsageResult(usedCount, limit, Math.max(limit - usedCount, 0),
			reservation.usageDate().plusDays(1).atStartOfDay());
	}

	private static long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}

}
